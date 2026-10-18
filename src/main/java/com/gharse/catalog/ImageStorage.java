package com.gharse.catalog;

import org.springframework.web.multipart.MultipartFile;

/** Storage behind product photos. Swap the implementation to move to cloud later. */
public interface ImageStorage {
    /** Saves the file and returns a public URL path, for example /uploads/products/abc.jpg. */
    String save(MultipartFile file);

    /** Deletes a stored file by its URL path. Missing files are ignored. */
    void delete(String url);
}