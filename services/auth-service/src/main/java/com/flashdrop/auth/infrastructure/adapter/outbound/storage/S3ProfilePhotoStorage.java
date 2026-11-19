package com.flashdrop.auth.infrastructure.adapter.outbound.storage;

import java.util.List;
import java.util.Optional;

import com.flashdrop.auth.application.port.outbound.ProfilePhotoStorage;
import com.flashdrop.auth.domain.exception.PhotoStorageUnavailableException;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Fotos de perfil en un bucket privado de Floci (mismo patron que
 * {@code S3ProductImageStorage} de catalog). El bucket nunca se expone: la foto
 * se lee siempre a traves de auth, con el token del usuario.
 */
public class S3ProfilePhotoStorage implements ProfilePhotoStorage {

    private final S3Client s3;
    private final String bucket;

    public S3ProfilePhotoStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .cacheControl("private, max-age=86400")
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new PhotoStorageUnavailableException("No se pudo guardar la foto de perfil", e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw new PhotoStorageUnavailableException("No se pudo leer la foto de perfil", e);
        } catch (SdkException e) {
            throw new PhotoStorageUnavailableException("No se pudo leer la foto de perfil", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            throw new PhotoStorageUnavailableException("No se pudo borrar la foto de perfil", e);
        }
    }

    @Override
    public void deleteAll(String prefix) {
        try {
            String token = null;
            do {
                ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .continuationToken(token)
                        .build());
                List<ObjectIdentifier> keys = page.contents().stream()
                        .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                        .toList();
                if (!keys.isEmpty()) {
                    s3.deleteObjects(DeleteObjectsRequest.builder()
                            .bucket(bucket)
                            .delete(Delete.builder().objects(keys).quiet(true).build())
                            .build());
                }
                token = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
            } while (token != null);
        } catch (SdkException e) {
            throw new PhotoStorageUnavailableException("No se pudieron borrar las fotos de perfil", e);
        }
    }
}
