package io.roadmap.todolistmonorepo.mappers;

import io.roadmap.todolistmonorepo.dto.UserCreateRequest;
import io.roadmap.todolistmonorepo.entities.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Mapper(componentModel = "spring")
public abstract class UserMapper {
    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Named("encodePassword")
    protected String encodePassword(String rawPassword) {
        return bCryptPasswordEncoder.encode(rawPassword);
    }


    @Mapping(target = "password",  qualifiedByName = "encodePassword")
    public abstract User toEntity(UserCreateRequest dto);
}