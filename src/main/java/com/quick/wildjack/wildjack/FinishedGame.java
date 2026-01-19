package com.quick.wildjack.wildjack;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "finished_games")
public class FinishedGame {
    @Id
    private String id;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private Instant finishedAt;
}
