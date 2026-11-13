package com.kedu.tago.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class GCSConfig {

    @Value("${gcp.credentials.location}")
    private Resource credLoc;

    @Value("${gcp.project.id}")
    private String projectId;

    @Bean // Spring Legacy의 <Bean> 태그와 같음
    public Storage storage() throws Exception {
        GoogleCredentials cred = GoogleCredentials.fromStream(credLoc.getInputStream());
        return StorageOptions.newBuilder()
                .setCredentials(cred)
                .setProjectId(projectId)
                .build()
                .getService();
    }

}
