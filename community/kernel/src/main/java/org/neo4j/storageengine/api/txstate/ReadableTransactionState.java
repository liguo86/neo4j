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
package org.neo4j.storageengine.api.txstate;

import java.util.Iterator;

import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.kernel.api.exceptions.schema.ConstraintValidationException;
import org.neo4j.kernel.api.exceptions.schema.CreateConstraintFailureException;
import org.neo4j.kernel.api.schema.OrderedPropertyValues;
import org.neo4j.kernel.api.schema.SchemaDescriptor;
import org.neo4j.kernel.api.schema.constaints.ConstraintDescriptor;
import org.neo4j.kernel.api.schema.index.IndexDescriptor;
import org.neo4j.kernel.impl.api.RelationshipVisitor;
import org.neo4j.kernel.impl.api.store.RelationshipIterator;
import org.neo4j.storageengine.api.StorageProperty;

/**
 * This interface contains the methods for reading transaction state from the transaction state.
 * The implementation of these methods should be free of any side effects (such as initialising lazy state).
 */
public interface ReadableTransactionState extends NodeTransactionStateView
{
    void accept( TxStateVisitor visitor ) throws ConstraintValidationException, CreateConstraintFailureException;

    boolean hasChanges();

    // ENTITY RELATED

    /**
     * Returns all nodes that, in this tx, have had labelId removed.
     */
    ReadableDiffSets<Long> nodesWithLabelChanged( int labelId );

    /**
     * Returns rels that have been added and removed in this tx.
     */
    ReadableRelationshipDiffSets<Long> addedAndRemovedRelationships();

    /**
     * Nodes that have had labels, relationships, or properties modified in this tx.
     */
    Iterable<NodeState> modifiedNodes();

    /**
     * Rels that have properties modified in this tx.
     */
    Iterable<RelationshipState> modifiedRelationships();

    boolean relationshipIsAddedInThisTx( long relationshipId );

    boolean relationshipIsDeletedInThisTx( long relationshipId );

    Iterator<StorageProperty> augmentGraphProperties( Iterator<StorageProperty> original );

    PrimitiveLongIterator augmentNodesGetAll( PrimitiveLongIterator committed );

    RelationshipIterator augmentRelationshipsGetAll( RelationshipIterator committed );

    /**
     * @return {@code true} if the relationship was visited in this state, i.e. if it was created
     * by this current transaction, otherwise {@code false} where the relationship might need to be
     * visited from the store.
     */
    <EX extends Exception> boolean relationshipVisit( long relId, RelationshipVisitor<EX> visitor ) throws EX;

    // SCHEMA RELATED

    ReadableDiffSets<IndexDescriptor> indexDiffSetsByLabel( int labelId );

    ReadableDiffSets<IndexDescriptor> indexChanges();

    Iterable<IndexDescriptor> constraintIndexesCreatedInTx();

    ReadableDiffSets<ConstraintDescriptor> constraintsChanges();

    ReadableDiffSets<ConstraintDescriptor> constraintsChangesForLabel( int labelId );

    ReadableDiffSets<ConstraintDescriptor> constraintsChangesForSchema( SchemaDescriptor descriptor );

    ReadableDiffSets<ConstraintDescriptor> constraintsChangesForRelationshipType( int relTypeId );

    Long indexCreatedForConstraint( ConstraintDescriptor constraint );

    ReadableDiffSets<Long> indexUpdatesForScan( IndexDescriptor index );

    ReadableDiffSets<Long> indexUpdatesForSeek( IndexDescriptor index, OrderedPropertyValues values );

    ReadableDiffSets<Long> indexUpdatesForRangeSeekByNumber( IndexDescriptor index,
                                                             Number lower, boolean includeLower,
                                                             Number upper, boolean includeUpper );

    ReadableDiffSets<Long> indexUpdatesForRangeSeekByString( IndexDescriptor index,
                                                             String lower, boolean includeLower,
                                                             String upper, boolean includeUpper );

    ReadableDiffSets<Long> indexUpdatesForRangeSeekByPrefix( IndexDescriptor index, String prefix );

    RelationshipState getRelationshipState( long id );

    /**
     * The way tokens are created is that the first time a token is needed it gets created in its own little
     * token mini-transaction, separate from the surrounding transaction that creates or modifies data that need it.
     * From the kernel POV it's interesting to know whether or not any tokens have been created in this tx state,
     * because then we know it's a mini-transaction like this and won't have to let transaction event handlers
     * know about it, for example.
     *
     * The same applies to schema changes, such as creating and dropping indexes and constraints.
     */
    boolean hasDataChanges();

    ReadableTransactionState EMPTY = new ReadableTransactionState()
    {
        @Override
        public void accept( TxStateVisitor visitor )
                throws ConstraintValidationException, CreateConstraintFailureException
        {

        }

        @Override
        public boolean hasChanges()
        {
            return false;
        }

        @Override
        public ReadableDiffSets<Long> nodesWithLabelChanged( int labelId )
        {
            return ReadableDiffSets.Empty.instance();
        }

        @Override
        public ReadableDiffSets<Long> addedAndRemovedNodes()
        {
            return ReadableDiffSets.Empty.instance();
        }

        @Override
        public ReadableRelationshipDiffSets<Long> addedAndRemovedRelationships()
        {
            return ReadableRelationshipDiffSets.Empty.instance();
        }

        @Override
        public Iterable<NodeState> modifiedNodes()
        {
            return Iterables.empty();
        }

        @Override
        public Iterable<RelationshipState> modifiedRelationships()
        {
            return Iterables.empty();
        }

        @Override
        public boolean relationshipIsAddedInThisTx( long relationshipId )
        {
            return false;
        }

        @Override
        public boolean relationshipIsDeletedInThisTx( long relationshipId )
        {
            return false;
        }

        @Override
        public Iterator<StorageProperty> augmentGraphProperties( Iterator<StorageProperty> original )
        {
            return original;
        }

        @Override
        public boolean nodeIsAddedInThisTx( long nodeId )
        {
            return false;
        }

        @Override
        public boolean nodeIsDeletedInThisTx( long nodeId )
        {
            return false;
        }

        @Override
        public PrimitiveLongIterator augmentNodesGetAll( PrimitiveLongIterator committed )
        {
            return committed;
        }

        @Override
        public RelationshipIterator augmentRelationshipsGetAll( RelationshipIterator committed )
        {
            return committed;
        }

        @Override
        public <EX extends Exception> boolean relationshipVisit( long relId, RelationshipVisitor<EX> visitor ) throws EX
        {
            return false;
        }

        @Override
        public ReadableDiffSets<IndexDescriptor> indexDiffSetsByLabel( int labelId )
        {
            return ReadableDiffSets.Empty.instance();
        }

        @Override
        public ReadableDiffSets<IndexDescriptor> indexChanges()
        {
            return ReadableDiffSets.Empty.instance();
        }

        @Override
        public Iterable<IndexDescriptor> constraintIndexesCreatedInTx()
        {
            return Iterables.empty();
        }

        @Override
        public ReadableDiffSets<ConstraintDescriptor> constraintsChanges()
        {
            return ReadableDiffSets.Empty.instance();
        }

        @Override
        public ReadableDiffSets<ConstraintDescriptor> constraintsChangesForLabel( int labelId )
        {
            return ReadableDiffSets.Empty.instance();
        }

        @Override
        public ReadableDiffSets<ConstraintDescriptor> constraintsChangesForSchema( SchemaDescriptor descriptor )
        {
            return ReadableDiffSets.Empty.instance();
        }

        @Override
        public ReadableDiffSets<ConstraintDescriptor> constraintsChangesForRelationshipType( int relTypeId )
        {
            return ReadableDiffSets.Empty.instance();
        }

        @Override
        public Long indexCreatedForConstraint( ConstraintDescriptor constraint )
        {
            return null;
        }

        @Override
        public ReadableDiffSets<Long> indexUpdatesForScan( IndexDescriptor index )
        {
            return ReadableDiffSets.Empty.instance();
        }

        @Override
        public ReadableDiffSets<Long> indexUpdatesForSeek( IndexDescriptor index, OrderedPropertyValues values )
        {
            return ReadableDiffSets.Empty.instance();
        }

        @Override
        public ReadableDiffSets<Long> indexUpdatesForRangeSeekByNumber( IndexDescriptor index, Number lower,
                boolean includeLower, Number upper, boolean includeUpper )
        {
            return ReadableDiffSets.Empty.instance();
        }

        @Override
        public ReadableDiffSets<Long> indexUpdatesForRangeSeekByString( IndexDescriptor index, String lower,
                boolean includeLower, String upper, boolean includeUpper )
        {
            return ReadableDiffSets.Empty.instance();
        }

        @Override
        public ReadableDiffSets<Long> indexUpdatesForRangeSeekByPrefix( IndexDescriptor index, String prefix )
        {
            return ReadableDiffSets.Empty.instance();
        }

        @Override
        public NodeState getNodeState( long id )
        {
            return NodeState.EMPTY;
        }

        @Override
        public RelationshipState getRelationshipState( long id )
        {
            return RelationshipState.EMPTY;
        }

        @Override
        public boolean hasDataChanges()
        {
            return false;
        }
    };
}
