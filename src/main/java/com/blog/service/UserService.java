package com.blog.service;

import com.blog.dto.LoginDTO;

import java.util.Map;

/**
 * User / authentication service.
 */
public interface UserService {

    /**
     * Authenticate and return a JWT token plus user info.
     */
    Map<String, Object> login(LoginDTO loginDTO);
}
