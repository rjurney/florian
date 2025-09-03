package com.yangdb.fuse.assembly;

import com.typesafe.config.Config;
import com.yangdb.fuse.client.BaseFuseClient;
import com.yangdb.fuse.client.FuseClient;
import com.yangdb.fuse.dispatcher.urlSupplier.DefaultAppUrlSupplier;
import com.yangdb.fuse.services.FuseApp;
import com.yangdb.fuse.services.FuseUtils;
import com.yangdb.fuse.test.framework.index.ElasticEmbeddedNode;
import com.yangdb.fuse.test.framework.index.GlobalElasticEmbeddedNode;
import org.opensearch.client.transport.TransportClient;
import org.jooby.Jooby;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class Setup {
    public static Path path = Paths.get( "src","resources", "assembly", "Dragons", "config", "application.test.engine3.m1.dfs.dragons.public.conf");
    public static String userDir = Paths.get( "src","resources", "assembly", "Dragons").toFile().getAbsolutePath();

    public static ElasticEmbeddedNode elasticEmbeddedNode = null;
    public static FuseApp app = null;
    public static FuseClient fuseClient = null;
    public static TransportClient client = null;

    public static void withPath(Path path) {
        Setup.path = path;
    }

    public static void setup() throws Exception {
        setup(true);
    }

    public static void setup(boolean embedded) throws Exception {
        setup(embedded,true);
    }

    public static void setup(boolean embedded, boolean init) throws Exception {
        init(embedded,init,true);
        fuseClient = new BaseFuseClient("http://localhost:8888/fuse");
    }

    public static void setup(boolean embedded, boolean init,boolean startFuse) throws Exception {
        init(embedded,init,startFuse);
        fuseClient = new BaseFuseClient("http://localhost:8888/fuse");
    }

    public static void setup(boolean embedded, boolean init, boolean startFuse, FuseClient givenFuseClient) throws Exception {
        init(embedded,init,startFuse);
        //set fuse client
        fuseClient = givenFuseClient;
    }

    private static void init(boolean embedded, boolean init, boolean startFuse) throws Exception {
        //set location aware user directory
        System.setProperty("user.dir",userDir);
        // Start embedded ES
        if(embedded) {
            elasticEmbeddedNode = GlobalElasticEmbeddedNode.getInstance("Dragons");
            client = ElasticEmbeddedNode.getClient();
        } else {
            //use existing running ES
            client = ElasticEmbeddedNode.getClient("Dragons", 9300);
        }

        if(init) {
            //todo some init stuff
        }

        startFuse(startFuse);
    }

    private static void startFuse(boolean startFuse) {
        // Start fuse app (based on Jooby app web server)
        if(startFuse) {
            // Load fuse engine config file
            String confFilePath = path.toString();
            //load configuration
            Config config = FuseUtils.loadConfig(new File(confFilePath),"activeProfile" );
            String[] joobyArgs = new String[]{
                    "logback.configurationFile="+Paths.get("src", "test","resources", "config", "logback.xml").toString() ,
                    "server.join=false"
            };

            app = new FuseApp(new DefaultAppUrlSupplier("/fuse"))
                    .conf(path.toFile(), "activeProfile");
            app.start("server.join=false");
        }
    }


    public static void cleanup() throws Exception {
        if (app != null) {
            app.stop();
        }

        GlobalElasticEmbeddedNode.close();
    }
}
