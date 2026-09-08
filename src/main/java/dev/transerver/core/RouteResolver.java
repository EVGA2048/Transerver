package dev.transerver.core;

import java.net.URI;
import java.util.Optional;

@FunctionalInterface
public interface RouteResolver {
    Optional<URI> resolve(String destination);
}
