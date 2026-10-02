package com.flashdrop.catalog.application.port.outbound;

import com.flashdrop.catalog.application.storage.ProductImageContent;
import com.flashdrop.catalog.application.storage.StoredProductImage;

public interface ProductImageStorage {

    StoredProductImage store(byte[] content, String contentType, String extension);

    ProductImageContent load(String objectKey);
}
