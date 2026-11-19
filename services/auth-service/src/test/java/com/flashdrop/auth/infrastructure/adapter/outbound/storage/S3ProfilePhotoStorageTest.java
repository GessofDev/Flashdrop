package com.flashdrop.auth.infrastructure.adapter.outbound.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.flashdrop.auth.domain.exception.PhotoStorageUnavailableException;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/** F10: el adaptador habla con el bucket privado y traduce sus fallas a 503. */
class S3ProfilePhotoStorageTest {

    private static final String BUCKET = "flashdrop-avatars";
    private static final String KEY = "avatars/7/abc.jpg";

    private final S3Client s3 = mock(S3Client.class);
    private final S3ProfilePhotoStorage storage = new S3ProfilePhotoStorage(s3, BUCKET);

    @Test
    void guardaEnElBucketConfiguradoComoJpegPrivado() {
        storage.put(KEY, new byte[] {1, 2}, "image/jpeg");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals(KEY, request.getValue().key());
        assertEquals("image/jpeg", request.getValue().contentType());
        assertTrue(request.getValue().cacheControl().startsWith("private"));
    }

    @Test
    void siFlociNoRespondeAlGuardarEs503() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("connection refused"));

        assertThrows(PhotoStorageUnavailableException.class, () -> storage.put(KEY, new byte[] {1}, "image/jpeg"));
    }

    @Test
    void leeLosBytesDelObjeto() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {4, 5}));

        assertArrayEquals(new byte[] {4, 5}, storage.get(KEY).orElseThrow());
    }

    @Test
    void unObjetoQueNoExisteSeDevuelveVacio() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().statusCode(404).message("no").build());

        assertTrue(storage.get(KEY).isEmpty());
    }

    @Test
    void otroErrorDeS3AlLeerEs503() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("boom").build());

        assertThrows(PhotoStorageUnavailableException.class, () -> storage.get(KEY));
    }

    @Test
    void borraUnObjeto() {
        storage.delete(KEY);

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(request.capture());
        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals(KEY, request.getValue().key());
    }

    /** F5: borra todas las paginas bajo el prefijo del usuario. */
    @Test
    void borrarPorPrefijoRecorreTodasLasPaginas() {
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("avatars/7/a.jpg").build())
                        .isTruncated(true).nextContinuationToken("t2").build(),
                ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("avatars/7/b.jpg").build())
                        .isTruncated(false).build());

        storage.deleteAll("avatars/7/");

        ArgumentCaptor<ListObjectsV2Request> list = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3, times(2)).listObjectsV2(list.capture());
        assertEquals("avatars/7/", list.getAllValues().get(0).prefix());
        assertEquals("t2", list.getAllValues().get(1).continuationToken());
        ArgumentCaptor<DeleteObjectsRequest> delete = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3, times(2)).deleteObjects(delete.capture());
        assertEquals(List.of("avatars/7/a.jpg", "avatars/7/b.jpg"), delete.getAllValues().stream()
                .flatMap(d -> d.delete().objects().stream()).map(o -> o.key()).toList());
    }

    @Test
    void borrarPorPrefijoSinObjetosNoLlamaAlBorrado() {
        when(s3.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().isTruncated(false).build());

        storage.deleteAll("avatars/7/");

        verify(s3, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void siFlociNoRespondeAlBorrarPorPrefijoEs503() {
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(SdkClientException.create("down"));

        assertThrows(PhotoStorageUnavailableException.class, () -> storage.deleteAll("avatars/7/"));
    }
}
