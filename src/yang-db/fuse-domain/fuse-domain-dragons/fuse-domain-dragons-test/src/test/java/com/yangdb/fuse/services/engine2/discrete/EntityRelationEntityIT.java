package com.yangdb.fuse.services.engine2.discrete;

import com.yangdb.fuse.client.BaseFuseClient;
import com.yangdb.fuse.model.ontology.Ontology;
import com.yangdb.fuse.model.query.*;
import com.yangdb.fuse.model.query.entity.ETyped;
import com.yangdb.fuse.model.query.properties.constraint.Constraint;
import com.yangdb.fuse.model.query.properties.constraint.ConstraintOp;
import com.yangdb.fuse.model.query.properties.EProp;
import com.yangdb.fuse.model.query.quant.Quant1;
import com.yangdb.fuse.model.query.quant.QuantType;
import com.yangdb.fuse.model.resourceInfo.CursorResourceInfo;
import com.yangdb.fuse.model.resourceInfo.FuseResourceInfo;
import com.yangdb.fuse.model.resourceInfo.PageResourceInfo;
import com.yangdb.fuse.model.resourceInfo.QueryResourceInfo;
import com.yangdb.fuse.model.results.AssignmentsQueryResult;
import com.yangdb.fuse.model.transport.cursor.CreateGraphCursorRequest;
import com.yangdb.fuse.client.FuseClient;
import com.yangdb.fuse.test.framework.index.ElasticEmbeddedNode;
import com.yangdb.fuse.test.framework.index.MappingElasticConfigurer;
import com.yangdb.fuse.test.framework.index.Mappings;
import com.yangdb.fuse.test.framework.index.Mappings.Mapping;
import com.yangdb.fuse.test.framework.populator.ElasticDataPopulator;
import com.yangdb.test.BaseITMarker;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.client.transport.TransportClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static com.yangdb.fuse.model.OntologyTestUtils.*;
import static com.yangdb.fuse.model.OntologyTestUtils.NAME;
import static com.yangdb.fuse.test.framework.index.Mappings.Mapping.Property.Type.keyword;

/**
 * Created by roman.margolis on 02/10/2017.
 */
public class EntityRelationEntityIT implements BaseITMarker {
    //region setup
    @BeforeClass
    public static void setup() throws Exception {
        fuseClient = new BaseFuseClient("http://localhost:8888/fuse");

        FuseResourceInfo fuseResourceInfo = fuseClient.getFuseInfo();
        $ont = new Ontology.Accessor(fuseClient.getOntology(fuseResourceInfo.getCatalogStoreUrl() + "/Dragons"));

        String idField = "id";

        TransportClient client = ElasticEmbeddedNode.getClient();

        new MappingElasticConfigurer(Arrays.asList("person1", "person2"), new Mappings().addMapping("pge",
                new Mapping().addProperty("type", new Mapping.Property(keyword))
                        .addProperty("name", new Mapping.Property(keyword)))).configure(client);

        new ElasticDataPopulator(
                client,
                "person1",
                "pge",
                idField,
                true,
                null,
                false,
                () -> createPeople(0, 5)).populate();

        new ElasticDataPopulator(
                client,
                "person2",
                "pge",
                idField,
                true,
                null,
                false,
                () -> createPeople(5, 10)).populate();

        new MappingElasticConfigurer(Arrays.asList("dragon1", "dragon2"), new Mappings().addMapping("pge",
                new Mapping().addProperty("type", new Mapping.Property(keyword))
                        .addProperty("name", new Mapping.Property(keyword))
                        .addProperty("personId", new Mapping.Property(keyword)))).configure(client);

        new ElasticDataPopulator(
                client,
                "dragon1",
                "pge",
                idField,
                true,
                "personId",
                false,
                () -> createDragons(0, 5, 3)).populate();

        new ElasticDataPopulator(
                client,
                "dragon2",
                "pge",
                idField,
                true,
                "personId",
                false,
                () -> createDragons(5, 10, 3)).populate();

        client.admin().indices().refresh(new RefreshRequest("person1", "person2", "dragon1", "dragon2")).actionGet();
    }

    @AfterClass
    public static void cleanup() throws Exception {
        ElasticEmbeddedNode.getClient().admin().indices()
                .delete(new DeleteIndexRequest("person1", "person2", "dragon1", "dragon2")).actionGet();
    }
    //endregion

    //region Tests
    @Test
    public void test_Person_own_Dragon_paths() throws IOException, InterruptedException {
        Query query = Query.Builder.instance().withName("q1").withOnt($ont.name()).withElements(Arrays.asList(
                new Start(0, 1),
                new ETyped(1, "A", PERSON.type, 2, 0),
                new Rel(2, OWN.getrType(), Rel.Direction.R, null, 3, 0),
                new ETyped(3, "B", DRAGON.type, 0, 0)
        )).build();

        FuseResourceInfo fuseResourceInfo = fuseClient.getFuseInfo();
        QueryResourceInfo queryResourceInfo = fuseClient.postQuery(fuseResourceInfo.getQueryStoreUrl(), query);
        CursorResourceInfo cursorResourceInfo = fuseClient.postCursor(queryResourceInfo.getCursorStoreUrl());
        PageResourceInfo pageResourceInfo = fuseClient.postPage(cursorResourceInfo.getPageStoreUrl(), 1000);

        while (!pageResourceInfo.isAvailable()) {
            pageResourceInfo = fuseClient.getPage(pageResourceInfo.getResourceUrl());
            if (!pageResourceInfo.isAvailable()) {
                Thread.sleep(10);
            }
        }

        AssignmentsQueryResult actualAssignmentsQueryResult = (AssignmentsQueryResult) fuseClient.getPageData(pageResourceInfo.getDataUrl());
        int x = 5;
    }

    @Test
    public void test_Person_own_Dragon_graph() throws IOException, InterruptedException {
        Query query = Query.Builder.instance().withName("q1").withOnt($ont.name()).withElements(Arrays.asList(
                new Start(0, 1),
                new ETyped(1, "A", PERSON.type, 2, 0),
                new Rel(2, OWN.getrType(), Rel.Direction.R, null, 3, 0),
                new ETyped(3, "B", DRAGON.type, 0, 0)
        )).build();

        FuseResourceInfo fuseResourceInfo = fuseClient.getFuseInfo();
        QueryResourceInfo queryResourceInfo = fuseClient.postQuery(fuseResourceInfo.getQueryStoreUrl(), query);
        CursorResourceInfo cursorResourceInfo = fuseClient.postCursor(
                queryResourceInfo.getCursorStoreUrl(),
                new CreateGraphCursorRequest());
        PageResourceInfo pageResourceInfo = fuseClient.postPage(cursorResourceInfo.getPageStoreUrl(), 1000);

        while (!pageResourceInfo.isAvailable()) {
            pageResourceInfo = fuseClient.getPage(pageResourceInfo.getResourceUrl());
            if (!pageResourceInfo.isAvailable()) {
                Thread.sleep(10);
            }
        }

        AssignmentsQueryResult actualAssignmentsQueryResult = (AssignmentsQueryResult) fuseClient.getPageData(pageResourceInfo.getDataUrl());
        int x = 5;
    }

    @Test
    public void test_person1_own_Dragon_paths() throws IOException, InterruptedException {
        Query query = Query.Builder.instance().withName("q1").withOnt($ont.name()).withElements(Arrays.asList(
                new Start(0, 1),
                new ETyped(1, "A", PERSON.type, 2, 0),
                new Quant1(2, QuantType.all, Arrays.asList(3, 4), 0),
                new EProp(3, NAME.type, Constraint.of(ConstraintOp.eq, "person1")),
                new Rel(4, OWN.getrType(), Rel.Direction.R, null, 5, 0),
                new ETyped(5, "B", DRAGON.type, 0, 0)
        )).build();

        FuseResourceInfo fuseResourceInfo = fuseClient.getFuseInfo();
        QueryResourceInfo queryResourceInfo = fuseClient.postQuery(fuseResourceInfo.getQueryStoreUrl(), query);
        CursorResourceInfo cursorResourceInfo = fuseClient.postCursor(queryResourceInfo.getCursorStoreUrl());
        PageResourceInfo pageResourceInfo = fuseClient.postPage(cursorResourceInfo.getPageStoreUrl(), 1000);

        while (!pageResourceInfo.isAvailable()) {
            pageResourceInfo = fuseClient.getPage(pageResourceInfo.getResourceUrl());
            if (!pageResourceInfo.isAvailable()) {
                Thread.sleep(10);
            }
        }

        AssignmentsQueryResult actualAssignmentsQueryResult = (AssignmentsQueryResult) fuseClient.getPageData(pageResourceInfo.getDataUrl());
        int x = 5;
    }
    //endregion

    //region Protected Methods
    protected static Iterable<Map<String, Object>> createPeople(int startId, int endId) {
        List<Map<String, Object>> people = new ArrayList<>();
        for(int i = startId ; i < endId ; i++) {
            Map<String, Object> person = new HashMap<>();
            person.put("id", "p" + String.format("%03d", i));
            person.put("type", "Person");
            person.put("name", "person" + i);
            people.add(person);
        }
        return people;
    }

    protected static Iterable<Map<String, Object>> createDragons(int personStartId, int personEndId, int numDragonsPerPerson) {
        int dragonId = personStartId * numDragonsPerPerson;

        List<Map<String, Object>> dragons = new ArrayList<>();
        for(int i = personStartId ; i < personEndId ; i++) {
            for (int j = 0; j < numDragonsPerPerson; j++) {
                Map<String, Object> dragon = new HashMap<>();
                dragon.put("id", "d" + String.format("%03d", dragonId));
                dragon.put("type", "Dragon");
                dragon.put("personId", "p" + String.format("%03d", i));
                dragon.put("name", "dragon" + dragonId);
                dragons.add(dragon);

                dragonId++;
            }
        }

        return dragons;
    }
    //endregion

    //region Fields
    private static FuseClient fuseClient;
    private static Ontology.Accessor $ont;
    //endregion
}
