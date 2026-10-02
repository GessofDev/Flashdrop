package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.flashdrop.catalog.application.port.outbound.ProductImageStorage;
import com.flashdrop.catalog.application.storage.ProductImageContent;
import com.flashdrop.catalog.application.storage.StoredProductImage;
import com.flashdrop.catalog.application.usecase.UploadProductImageUseCase;
import com.flashdrop.catalog.domain.exception.ImageStorageException;

@SpringBootTest(properties = {
        "INTERNAL_API_KEY=dev-key",
        "S3_PUBLIC_URL_BASE=/catalog/images"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ProductImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockitoBean
    private ProductImageStorage productImageStorage;

    @Test
    void upload_withoutJwt_returnsUnauthorizedEnvelope() throws Exception {
        mockMvc.perform(multipart("/api/catalog/my/products/image")
                        .file(jpegFile()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void upload_withoutRestaurantRole_returnsForbiddenEnvelope() throws Exception {
        mockMvc.perform(multipart("/api/catalog/my/products/image")
                        .file(jpegFile())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void upload_withRestaurantRole_returnsObjectKeyAndPublicUrl() throws Exception {
        StoredProductImage stored = new StoredProductImage(
                "products/2026/09/550e8400-e29b-41d4-a716-446655440000.jpg",
                "/catalog/images/products/2026/09/550e8400-e29b-41d4-a716-446655440000.jpg"
        );
        when(productImageStorage.store(any(byte[].class), eq("image/jpeg"), eq("jpg")))
                .thenReturn(stored);

        mockMvc.perform(multipart("/api/catalog/my/products/image")
                        .file(jpegFile())
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_Cliente"),
                                new SimpleGrantedAuthority("ROLE_Restaurante")
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objectKey").value(stored.objectKey()))
                .andExpect(jsonPath("$.url").value(stored.url()));
    }

    @Test
    void upload_withInvalidMime_returnsBadRequest() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "not-an-image.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not an image".getBytes()
        );

        mockMvc.perform(multipart("/api/catalog/my/products/image")
                        .file(textFile)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Restaurante"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void upload_largerThanFiveMegabytes_returnsPayloadTooLarge() throws Exception {
        byte[] oversized = new byte[UploadProductImageUseCase.MAX_IMAGE_BYTES + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                oversized
        );

        mockMvc.perform(multipart("/api/catalog/my/products/image")
                        .file(largeFile)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Restaurante"))))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void upload_whenStorageFails_returnsBadGateway() throws Exception {
        when(productImageStorage.store(any(byte[].class), eq("image/jpeg"), eq("jpg")))
                .thenThrow(new ImageStorageException("S3 no disponible"));

        mockMvc.perform(multipart("/api/catalog/my/products/image")
                        .file(jpegFile())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Restaurante"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("IMAGE_STORAGE_ERROR"));
    }

    @Test
    void jwtConverter_mapsEveryRoleFromListClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("4")
                .claim("roles", List.of("Cliente", "Restaurante", "Repartidor"))
                .build();

        var authentication = jwtAuthenticationConverter.convert(jwt);

        org.hamcrest.MatcherAssert.assertThat(
                authentication.getAuthorities().stream().map(Object::toString).toList(),
                hasItems("ROLE_Cliente", "ROLE_Restaurante", "ROLE_Repartidor")
        );
    }

    @Test
    void getImage_withoutJwt_returnsStoredBytes() throws Exception {
        String key = "products/2026/09/550e8400-e29b-41d4-a716-446655440000.webp";
        byte[] bytes = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        when(productImageStorage.load(key)).thenReturn(new ProductImageContent(bytes, "image/webp"));

        mockMvc.perform(get("/catalog/images/" + key))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/webp"))
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
    }

    private MockMultipartFile jpegFile() {
        return new MockMultipartFile(
                "file",
                "product.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );
    }
}
