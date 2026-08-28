package com.mypetadmin.ps_gateway.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VersionController {

    private final String version;
    private final String commit;

    public VersionController(
            @Value("${app.version:0.0.1-SNAPSHOT}") String version,
            @Value("${app.git-commit:dev}") String commit) {
        this.version = version;
        this.commit = commit;
    }

    @GetMapping("/version")
    VersionResponse version() {
        return new VersionResponse("ps-gateway", version, commit);
    }

    record VersionResponse(String service, String version, String commit) {
    }
}
