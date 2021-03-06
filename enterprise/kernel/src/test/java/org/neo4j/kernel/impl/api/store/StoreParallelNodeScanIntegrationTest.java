/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.api.store;

import org.junit.Rule;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.neo4j.cursor.Cursor;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.Exceptions;
import org.neo4j.kernel.impl.api.state.TxState;
import org.neo4j.kernel.impl.storageengine.impl.recordstorage.RecordStorageEngine;
import org.neo4j.kernel.impl.store.NodeStore;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.storageengine.api.BatchingLongProgression;
import org.neo4j.storageengine.api.BatchingProgressionFactory;
import org.neo4j.storageengine.api.NodeItem;
import org.neo4j.storageengine.api.txstate.NodeTransactionStateView;
import org.neo4j.test.rule.EnterpriseDatabaseRule;
import org.neo4j.test.rule.RandomRule;

import static java.util.Collections.disjoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.neo4j.kernel.impl.locking.LockService.NO_LOCK_SERVICE;
import static org.neo4j.storageengine.api.txstate.ReadableTransactionState.EMPTY;

public class StoreParallelNodeScanIntegrationTest
{
    @Rule
    public final EnterpriseDatabaseRule databaseRule = new EnterpriseDatabaseRule();
    @Rule
    public final RandomRule random = new RandomRule();

    @Test
    public void parallelScanShouldProvideTheSameResultAsANormalScan() throws Throwable
    {
        GraphDatabaseAPI db = databaseRule.getGraphDatabaseAPI();

        BatchingProgressionFactory progressionFactory = progressionFactory( db );
        NodeStore nodeStore = nodeStore( db );
        int nodes = randomNodes( nodeStore );
        createNodes( db, nodes );

        Set<Long> expected = singleThreadExecution( nodeStore, progressionFactory, EMPTY );

        int threads = random.nextInt( 2, 6 );
        ExecutorService executor = Executors.newCachedThreadPool();
        try
        {
            Set<Long> parallelResult = parallelExecution( nodeStore, executor, threads, progressionFactory, EMPTY );
            assertEquals( expected, parallelResult );
        }
        finally
        {
            executor.shutdown();
        }
    }

    @Test
    public void parallelScanWithTxStateChangesShouldProvideTheSameResultAsANormalScanWithTheSameChanges()
            throws Throwable
    {
        GraphDatabaseAPI db = databaseRule.getGraphDatabaseAPI();
        BatchingProgressionFactory progressionFactory = progressionFactory( db );
        NodeStore nodeStore = nodeStore( db );
        int nodes = randomNodes( nodeStore );
        long lastNodeId = createNodes( db, nodes );

        TxState txState = crateTxStateWithRandomAddedAndDeletedNodes( nodes, lastNodeId );

        Set<Long> expected = singleThreadExecution( nodeStore, progressionFactory, txState );

        ExecutorService executor = Executors.newCachedThreadPool();
        try
        {
            int threads = random.nextInt( 2, 6 );
            Set<Long> parallelResult = parallelExecution( nodeStore, executor, threads, progressionFactory, txState );
            assertEquals( expected, parallelResult );
        }
        finally
        {
            executor.shutdown();
        }
    }

    private Set<Long> parallelExecution( NodeStore nodeStore, ExecutorService executorService, int threads,
            BatchingProgressionFactory progressionFactory, NodeTransactionStateView stateView ) throws Throwable
    {
        NodeCursor[] nodeCursors = new NodeCursor[threads];
        for ( int i = 0; i < threads; i++ )
        {
            nodeCursors[i] = new NodeCursor( nodeStore, this::noCache, NO_LOCK_SERVICE );
        }
        // use any of the local statements to build the shared progression
        BatchingLongProgression progression = progressionFactory.parallelAllNodeScan( nodeStore );

        @SuppressWarnings( "unchecked" )
        Future<Set<Long>>[] futures = new Future[threads];
        for ( int i = 0; i < threads; i++ )
        {
            int id = i;
            futures[i] = executorService.submit( () ->
            {
                HashSet<Long> ids = new HashSet<>();
                try ( Cursor<NodeItem> cursor = nodeCursors[id].init( progression, stateView ) )
                {
                    while ( cursor.next() )
                    {
                        long nodeId = cursor.get().id();
                        assertTrue( ids.add( nodeId ) );
                    }
                }
                return ids;
            } );
        }

        Throwable t = null;
        @SuppressWarnings( "unchecked" )
        Set<Long> parallelResult = new HashSet<>();
        for ( int i = 0; i < threads; i++ )
        {
            try
            {
                Set<Long> set = futures[i].get();
                assertTrue( disjoint( parallelResult, set ) );
                parallelResult.addAll( set );
            }
            catch ( Throwable current )
            {
                t = Exceptions.chain( t, current );
            }
        }

        if ( t != null )
        {
            throw t;
        }

        return parallelResult;
    }

    private Set<Long> singleThreadExecution( NodeStore nodeStore, BatchingProgressionFactory progressionFactory,
            NodeTransactionStateView stateView )
    {
        Set<Long> expected = new HashSet<>();
        try ( Cursor<NodeItem> cursor = new NodeCursor( nodeStore, this::noCache, NO_LOCK_SERVICE )
                .init( progressionFactory.allNodeScan( nodeStore ), stateView ) )
        {
            while ( cursor.next() )
            {
                long nodeId = cursor.get().id();
                assertTrue( expected.add( nodeId ) );

            }
        }
        return expected;
    }

    private int randomNodes( NodeStore nodeStore )
    {
        int recordsPerPage = nodeStore.getRecordsPerPage();
        int pages = random.nextInt( 40, 1000 );
        int nonAlignedRecords = random.nextInt( 0, recordsPerPage - 1 );
        return pages * recordsPerPage + nonAlignedRecords;
    }

    private TxState crateTxStateWithRandomAddedAndDeletedNodes( int nodes, long lastNodeId )
    {
        TxState txState = new TxState();
        for ( long i = lastNodeId + 1; i <= lastNodeId + 100; i++ )
        {
            txState.nodeDoCreate( i );
        }

        for ( int i = 0; i < 100; i++ )
        {
            long id = random.nextLong( 0, nodes );
            txState.nodeDoDelete( id );
        }
        return txState;
    }

    private long createNodes( GraphDatabaseAPI db, int nodes )
    {
        try ( Transaction tx = db.beginTx() )
        {
            long nodeId = -1;
            for ( int j = 0; j < nodes; j++ )
            {
                nodeId = db.createNode().getId();
            }
            tx.success();
            return nodeId;
        }
    }

    private BatchingProgressionFactory progressionFactory( GraphDatabaseAPI db )
    {
        return db.getDependencyResolver().resolveDependency( BatchingProgressionFactory.class );
    }

    private NodeStore nodeStore( GraphDatabaseAPI db )
    {
        return db.getDependencyResolver().resolveDependency( RecordStorageEngine.class ).testAccessNeoStores()
                .getNodeStore();
    }

    private void noCache( NodeCursor c )
    {
    }
}
