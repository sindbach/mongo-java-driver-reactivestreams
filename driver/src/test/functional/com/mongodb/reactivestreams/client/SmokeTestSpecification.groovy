/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.reactivestreams.client

import com.mongodb.MongoNamespace
import com.mongodb.diagnostics.logging.Loggers
import org.bson.Document

import static Fixture.getMongoClient
import static java.util.concurrent.TimeUnit.SECONDS

class SmokeTestSpecification extends FunctionalSpecification {

    private static final LOGGER = Loggers.getLogger('smokeTest')

    def 'should handle common scenarios without error'() {
        given:
        def mongoClient = getMongoClient()
        def database = mongoClient.getDatabase(databaseName)
        def document = new Document('_id', 1)
        def updatedDocument = new Document('_id', 1).append('a', 1)

        when:
        run('clean up old database', mongoClient.getDatabase(databaseName).&dropDatabase) == null
        def names = run('get database names', mongoClient.&getDatabaseNames)

        then: 'Get Database Names'
        !names.contains(null)

        then:
        run('Create a collection and the created database is in the list', database.&createCollection, collectionName)[0] == null

        when:
        def updatedNames = run('get database names', mongoClient.&getDatabaseNames)

        then: 'The database names should contain the database and be one bigger than before'
        updatedNames.contains(databaseName)
        updatedNames.size() == names.size() + 1

        when:
        def collectionNames = run('The collection name should be in the collection names list', database.&getCollectionNames)

        then:
        !collectionNames.contains(null)
        collectionNames.contains(collectionName)

        then:
        run('The count is zero', collection.&count)[0] == 0

        then:
        run('find first should return null if no documents', collection.find().&first)[0] == null

        then:
        run('Insert a document', collection.&insertOne, document)[0] == null

        then:
        run('The count is one', collection.&count)[0] == 1

        then:
        run('find that document', collection.find().&first)[0] == document

        then:
        run('update that document', collection.&updateOne, document, new Document('$set', new Document('a', 1)))[0].wasAcknowledged()

        then:
        run('find the updated document', collection.find().&first)[0] == updatedDocument

        then:
        run('aggregate the collection', collection.&aggregate, [new Document('$match', new Document('a', 1))])[0] == updatedDocument

        then:
        run('remove all documents', collection.&deleteOne, new Document())[0].getDeletedCount() == 1

        then:
        run('The count is zero', collection.&count)[0] == 0

        then:
        run('create an index', collection.&createIndex, new Document('test', 1))[0] == null

        then:
        def indexNames = run('has the newly created index', collection.&getIndexes)*.name

        then:
        indexNames.containsAll('_id_', 'test_1')

        then:
        run('drop the index', collection.&dropIndex, 'test_1')[0] == null

        then:
        run('has a single index left "_id" ', collection.&getIndexes).size == 1

        then:
        def newCollectionName = 'new' + collectionName.capitalize()
        run('can rename the collection', collection.&renameCollection, new MongoNamespace(databaseName, newCollectionName))[0] == null

        then:
        !run('the new collection name is in the collection names list', database.&getCollectionNames).contains(collectionName)
        run('get collection names', database.&getCollectionNames).contains(newCollectionName)

        when:
        collection = database.getCollection(newCollectionName)

        then:
        run('drop the collection', collection.&dropCollection)[0] == null

        then:
        run('there are no indexes', collection.&getIndexes).size == 0

        then:
        !run('the collection name is no longer in the collectionNames list', database.&getCollectionNames).contains(collectionName)
    }

    def 'should visit all documents from a cursor with multiple batches'() {
        given:
        def documents = (1..1000).collect { new Document('_id', it) }
        run('Insert 1000 documents', collection.&insertMany, documents)

        when:
        def subscriber = new Fixture.ObservableSubscriber<Document>()
        collection.find(new Document()).sort(new Document('_id', 1)).batchSize(99).subscribe(subscriber)
        def foundDocuments = subscriber.get(10, SECONDS)

        then:
        foundDocuments.size() == documents.size()
        foundDocuments == documents
    }

    def run(String log, operation, ... args) {
        LOGGER.debug(log);
        def subscriber = new Fixture.ObservableSubscriber()
        operation.call(args).subscribe(subscriber)
        subscriber.get(30, SECONDS)
    }

}