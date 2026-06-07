package ie.ucd.bdic.group6.network.message;

import ie.ucd.bdic.group6.core.snapshot.SessionSnapshot;

import java.io.Serial;
import java.util.Objects;

public record ServerUpdate(SessionSnapshot snapshot) implements Message {
    @Serial
    private static final long serialVersionUID = 1L;

    public ServerUpdate {
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
