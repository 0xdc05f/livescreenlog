package com.livescreenlog.app.domain;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserProjectId implements Serializable {
    private Long user;
    private Long project;
}
