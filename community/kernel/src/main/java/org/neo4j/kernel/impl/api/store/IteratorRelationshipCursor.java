/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.api.store;

import java.util.function.Consumer;

import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.kernel.impl.locking.LockService;
import org.neo4j.kernel.impl.store.RelationshipStore;
import org.neo4j.storageengine.api.txstate.ReadableTransactionState;

import static org.neo4j.collection.primitive.PrimitiveLongCollections.toPrimitiveIterator;
import static org.neo4j.kernel.impl.store.record.RecordLoad.CHECK;

/**
 * Cursor for iterating a set of relationships.
 */
public class IteratorRelationshipCursor extends AbstractIteratorRelationshipCursor
{
    private final Consumer<IteratorRelationshipCursor> instanceCache;
    private PrimitiveLongIterator iterator;

    IteratorRelationshipCursor( RelationshipStore relationshipStore,
            Consumer<IteratorRelationshipCursor> instanceCache,
            LockService lockService )
    {
        super( relationshipStore, lockService );
        this.instanceCache = instanceCache;
    }

    public IteratorRelationshipCursor init( PrimitiveLongIterator iterator, ReadableTransactionState state )
    {
        internalInitTxState( state, addedRelationships( state ) );
        this.iterator = iterator;
        return this;
    }

    private PrimitiveLongIterator addedRelationships( ReadableTransactionState state )
    {
        return toPrimitiveIterator( state.addedAndRemovedRelationships().getAdded().iterator() );
    }

    @Override
    protected boolean doFetchNext()
    {
        while ( iterator != null && iterator.hasNext() )
        {
            if ( relationshipStore.readRecord( iterator.next(), relationshipRecord, CHECK, cursor ).inUse() )
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public void close()
    {
        super.close();
        iterator = null;
        instanceCache.accept( this );
    }
}
