package com.yangdb.fuse.services.engine2;

import com.yangdb.fuse.dispatcher.urlSupplier.DefaultAppUrlSupplier;
import com.yangdb.fuse.services.FuseApp;
import com.yangdb.fuse.services.engine2.data.JoinE2EIT;
import com.yangdb.fuse.test.framework.index.ElasticEmbeddedNode;
import com.yangdb.test.BaseSuiteMarker;
import org.jooby.Jooby;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.io.File;
import java.nio.file.Paths;

/**
 * Created by Roman on 21/06/2017.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        JoinE2EIT.class
})
public class JoinE2ETestSuite implements BaseSuiteMarker {

    @BeforeClass
    public static void setup() throws Exception {
        System.out.println("JoinE2ETestSuite start");
        start = System.currentTimeMillis();

//        elasticEmbeddedNode = GlobalElasticEmbeddedNode.getInstance("Dragons");

        app = new FuseApp(new DefaultAppUrlSupplier("/fuse"))
                .conf(new File(Paths.get("src", "test", "conf", "application.engine2.dev.M2.discrete.conf").toString()), "m2.smartEpb");

        app.start("server.join=false");
    }

    @AfterClass
    public static void cleanup() throws Exception {
        if (app != null) {
            app.stop();
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("JoinE2ETestSuite elapsed: " + elapsed);
    }

    //region Fields
    private static long start;
    private static Jooby app;
    public static ElasticEmbeddedNode elasticEmbeddedNode;
    //endregion
}
