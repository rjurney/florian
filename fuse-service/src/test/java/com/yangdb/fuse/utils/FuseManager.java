package com.yangdb.fuse.utils;

import com.yangdb.fuse.client.BaseFuseClient;
import com.yangdb.fuse.client.FuseClient;
import com.yangdb.fuse.dispatcher.urlSupplier.DefaultAppUrlSupplier;
import com.yangdb.fuse.services.FuseApp;
import com.yangdb.fuse.services.FuseRunner;

import java.io.File;
import java.nio.file.Paths;

public class FuseManager {

    public FuseManager(String confFile, String activeProfile) {
        this.confFile = confFile;
        this.activeProfile = activeProfile;
    }

    public void init() throws Exception {
        startFuse();
    }

    public void cleanup() {
        teardownFuse();
    }

    private void teardownFuse() {
        if (fuseApp != null) {
            fuseApp.stop();
        }
    }

    private void startFuse() throws Exception {

        fuseApp = new FuseApp(new DefaultAppUrlSupplier("/fuse"))
                .conf(new File(Paths.get("fuse-test", "fuse-benchmarks-test", "src", "main", "resources", "conf", confFile).toString()), activeProfile);

        fuseApp.start("server.join=false");

        fuseClient = new BaseFuseClient("http://localhost:8888/fuse");
    }

    public FuseApp getFuseApp() {
        return fuseApp;
    }

    public FuseClient getFuseClient() {
        return fuseClient;
    }

    private String confFile;
    private String activeProfile;

    private FuseApp fuseApp;
    private FuseClient fuseClient;


}
