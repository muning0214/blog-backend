package com.blog.service;

import com.blog.entity.Tag;

import java.util.List;
import java.util.Map;

/**
 * Tag business service.
 */
public interface TagService {

    /** List all tags with published article count (public). */
    List<Map<String, Object>> listWithCount();

    /** List all tags for admin. */
    List<Tag> listAll();

    /** Create a tag. */
    Tag create(Tag tag);

    /** Update a tag. */
    Tag update(Long id, Tag tag);

    /** Logically delete a tag. */
    void delete(Long id);
}
