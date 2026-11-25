package io.roadmap.tasktracker.backend.mappers;

import io.roadmap.tasktracker.backend.dto.UserCreateRequest;
import io.roadmap.tasktracker.backend.entities.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Mapper(componentModel = "spring")
public abstract class UserMapper {
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Named("encodePassword")
    protected String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }


    @Mapping(target = "password",  qualifiedByName = "encodePassword")
    public abstract User toEntity(UserCreateRequest dto);
}