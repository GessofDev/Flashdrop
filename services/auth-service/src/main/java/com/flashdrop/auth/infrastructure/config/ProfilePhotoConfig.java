package com.flashdrop.auth.infrastructure.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.flashdrop.auth.application.port.inbound.ProfilePhotoUseCase;
import com.flashdrop.auth.application.port.outbound.AuditLogger;
import com.flashdrop.auth.application.port.outbound.ProfilePhotoProcessor;
import com.flashdrop.auth.application.port.outbound.ProfilePhotoStorage;
import com.flashdrop.auth.application.port.outbound.RateLimiter;
import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.application.usecase.ProfilePhotoService;
import com.flashdrop.auth.infrastructure.adapter.outbound.image.ImageIoProfilePhotoProcessor;
import com.flashdrop.auth.infrastructure.adapter.outbound.storage.S3ProfilePhotoStorage;
import com.flashdrop.auth.infrastructure.adapter.outbound.storage.UnconfiguredProfilePhotoStorage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Cableado de la foto de perfil (F10). */
@Configuration
@EnableConfigurationProperties(PhotoStorageProperties.class)
public class ProfilePhotoConfig {

    @Bean
    ProfilePhotoStorage profilePhotoStorage(PhotoStorageProperties props) {
        if (!props.isEnabled()) {
            return new UnconfiguredProfilePhotoStorage();
        }
        // Encendido con datos incompletos: mejor no arrancar que descubrirlo con el primer usuario.
        if (props.getEndpoint().isBlank() || props.getBucket().isBlank() || props.getRegion().isBlank()
                || props.getAccessKey().isBlank() || props.getSecretKey().isBlank()) {
            throw new IllegalStateException("AUTH_PHOTO_STORAGE_ENABLED=true requiere S3_ENDPOINT, "
                    + "S3_AVATARS_BUCKET, S3_REGION, S3_ACCESS_KEY y S3_SECRET_KEY");
        }
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .forcePathStyle(true)
                .build();
        return new S3ProfilePhotoStorage(client, props.getBucket());
    }

    @Bean
    ProfilePhotoProcessor profilePhotoProcessor() {
        return new ImageIoProfilePhotoProcessor();
    }

    @Bean
    ProfilePhotoUseCase profilePhotoUseCase(UserRepository users, ProfilePhotoStorage storage,
                                            ProfilePhotoProcessor processor, RateLimiter rateLimiter,
                                            AuditLogger audit) {
        return new ProfilePhotoService(users, storage, processor, rateLimiter, audit);
    }
}
