package com.yangdb.fuse.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.yangdb.fuse.dispatcher.ontology.IndexProviderFactory;
import com.yangdb.fuse.dispatcher.ontology.OntologyProvider;
import com.yangdb.fuse.executor.opensearch.OpensearchIndexProviderMappingFactoryIT;
import com.yangdb.fuse.executor.ontology.schema.*;
import com.yangdb.fuse.model.ontology.Ontology;
import com.yangdb.fuse.model.schema.IndexProvider;
import com.yangdb.fuse.test.framework.index.ElasticEmbeddedNode;
import com.yangdb.fuse.test.framework.index.GlobalElasticEmbeddedNode;
import com.yangdb.fuse.unipop.schemaProviders.GraphElementSchemaProvider;
import com.yangdb.fuse.unipop.schemaProviders.indexPartitions.IndexPartitions;
import com.yangdb.test.BaseSuiteMarker;
import org.opensearch.Version;
import org.opensearch.action.main.MainResponse;
import org.opensearch.client.Client;
import org.jooby.Jooby;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.mockito.Mockito;
import org.opensearch.cluster.ClusterName;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.yangdb.fuse.executor.ontology.schema.IndexProviderRawSchema.getIndexPartitions;
import static com.yangdb.fuse.test.framework.index.ElasticEmbeddedNode.FUSE_TEST_ELASTIC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Created by Roman on 21/06/2017.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        GraphInitiatorIT.class,
        IndexProviderBasedGraphLoaderIT.class,
        IndexProviderBasedCSVLoaderIT.class,
        OpensearchIndexProviderMappingFactoryIT.class
})
public class TestSuiteIndexProviderSuite implements BaseSuiteMarker {
    private static ElasticEmbeddedNode elasticEmbeddedNode;

    public static ObjectMapper mapper = new ObjectMapper();
    public static Config config;
    public static Ontology ontology;

    public static RawSchema nestedSchema, embeddedSchema, singleIndexSchema;
    public static IndexProvider nestedProvider, embeddedProvider, singleIndexProvider;

    public static OntologyProvider ontologyProvider;
    public static IndexProviderFactory nestedProviderIfc, embeddedProviderIfc, singleIndexProviderFactory;

    public static Client client;

    public static void setUpInternal() throws Exception {
        client = ElasticEmbeddedNode.getClient();
        InputStream providerNestedStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("schema/DragonsIndexProviderNested.conf");
        InputStream providerEmbeddedStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("schema/DragonsIndexProviderEmbedded.conf");
        InputStream providerSingleIndexStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("schema/DragonsSingleIndexProvider.conf");
        InputStream ontologyStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("schema/Dragons.json");

        nestedProvider = mapper.readValue(providerNestedStream, IndexProvider.class);
        embeddedProvider = mapper.readValue(providerEmbeddedStream, IndexProvider.class);
        singleIndexProvider = mapper.readValue(providerSingleIndexStream, IndexProvider.class);
        ontology = mapper.readValue(ontologyStream, Ontology.class);


        nestedProviderIfc = Mockito.mock(IndexProviderFactory.class);
        when(nestedProviderIfc.get(any())).thenAnswer(invocationOnMock -> Optional.of(nestedProvider));

        embeddedProviderIfc = Mockito.mock(IndexProviderFactory.class);
        when(embeddedProviderIfc.get(any())).thenAnswer(invocationOnMock -> Optional.of(embeddedProvider));

        singleIndexProviderFactory = Mockito.mock(IndexProviderFactory.class);
        when(singleIndexProviderFactory.get(any())).thenAnswer(invocationOnMock -> Optional.of(singleIndexProvider));

        ontologyProvider = Mockito.mock(OntologyProvider.class);
        when(ontologyProvider.get(any())).thenAnswer(invocationOnMock -> Optional.of(ontology));

        config = Mockito.mock(Config.class);
        when(config.getString(any())).thenAnswer(invocationOnMock -> "Dragons");

        GraphElementSchemaProvider nestedSchemaProvider = new GraphElementSchemaProviderJsonFactory(config, nestedProviderIfc, ontologyProvider).get(ontology);
        GraphElementSchemaProvider embeddedSchemaProvider = new GraphElementSchemaProviderJsonFactory(config, embeddedProviderIfc, ontologyProvider).get(ontology);
        GraphElementSchemaProvider singleIndexSchemaProvider = new GraphElementSchemaProviderJsonFactory(config, singleIndexProviderFactory, ontologyProvider).get(ontology);

        nestedSchema = new RawSchema() {
            @Override
            public IndexPartitions getPartition(String type) {
                return getIndexPartitions(nestedSchemaProvider, type);
            }

            @Override
            public String getIdPrefix(String type) {
                return "";
            }

            @Override
            public String getIdFormat(String type) {
                return "";
            }

            @Override
            public String getIndexPrefix(String type) {
                return "";
            }

            @Override
            public List<IndexPartitions.Partition> getPartitions(String type) {
                return StreamSupport.stream(getPartition(type).getPartitions().spliterator(), false)
                        .collect(Collectors.toList());

            }

            @Override
            public Iterable<String> indices() {
                return IndexProviderRawSchema.indices(nestedSchemaProvider);
            }
        };

        embeddedSchema = new RawSchema() {
            @Override
            public IndexPartitions getPartition(String type) {
                return getIndexPartitions(embeddedSchemaProvider, type);
            }

            @Override
            public String getIdPrefix(String type) {
                return "";
            }

            @Override
            public String getIdFormat(String type) {
                return "";
            }

            @Override
            public String getIndexPrefix(String type) {
                return "";
            }

            @Override
            public List<IndexPartitions.Partition> getPartitions(String type) {
                return StreamSupport.stream(getPartition(type).getPartitions().spliterator(), false)
                        .collect(Collectors.toList());

            }

            @Override
            public Iterable<String> indices() {
                return IndexProviderRawSchema.indices(embeddedSchemaProvider);
            }
        };

        singleIndexSchema = new RawSchema() {
            @Override
            public IndexPartitions getPartition(String type) {
                return getIndexPartitions(singleIndexSchemaProvider, type);
            }

            @Override
            public String getIdPrefix(String type) {
                return "";
            }

            @Override
            public String getIdFormat(String type) {
                return "";
            }

            @Override
            public String getIndexPrefix(String type) {
                return "";
            }

            @Override
            public List<IndexPartitions.Partition> getPartitions(String type) {
                return StreamSupport.stream(getPartition(type).getPartitions().spliterator(), false)
                        .collect(Collectors.toList());

            }

            @Override
            public Iterable<String> indices() {
                return IndexProviderRawSchema.indices(singleIndexSchemaProvider);
            }
        };
    }

    @BeforeClass
    public static void setup() throws Exception {
        init(true);
        //init elasticsearch provider mapping factory
        setUpInternal();
    }

    private static void init(boolean embedded) throws Exception {
        //first verify no instance is running already
        Optional<org.opensearch.client.core.MainResponse> info = GlobalElasticEmbeddedNode.isRunningLocally();
        // Start embedded ES
        if (embedded && !info.isPresent()) {
            info = Optional.of(getDefaultInfo());
            elasticEmbeddedNode = GlobalElasticEmbeddedNode.getInstance(info.get().getNodeName());
        }
        //use existing running ES
        client = ElasticEmbeddedNode.getClient(info.orElseGet(TestSuiteIndexProviderSuite::getDefaultInfo));
    }

    private static org.opensearch.client.core.MainResponse getDefaultInfo() {
        return new org.opensearch.client.core.MainResponse(FUSE_TEST_ELASTIC, null, ClusterName.DEFAULT.toString(),null);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        GlobalElasticEmbeddedNode.close();
    }


    public static Client getClient() {
        return client;
    }

    //region Fields
    private static Jooby app;
    //endregion
}
