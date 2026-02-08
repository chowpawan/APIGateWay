package com.apigateway.router;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Route manager for API Gateway
 * Handles path-based and header-based routing
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "gateway")
public class RouteManager {

    private List<Route> routes = new ArrayList<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @PostConstruct
    public void init() {
        // Sort routes by priority
        routes.sort(Comparator.comparingInt(Route::getPriority));
        log.info("Initialized {} routes", routes.size());
        routes.forEach(r -> log.info("Route: {} -> {}", r.getPath(), r.getDestinationUrl()));
    }

    /**
     * Find matching route for given path
     */
    public Optional<Route> findRoute(String path) {
        return routes.stream()
                .filter(Route::isEnabled)
                .filter(route -> pathMatcher.match(route.getPath(), path))
                .findFirst();
    }

    /**
     * Find route by ID
     */
    public Optional<Route> findRouteById(String routeId) {
        return routes.stream()
                .filter(r -> r.getId().equals(routeId))
                .findFirst();
    }

    /**
     * Get all routes
     */
    public List<Route> getAllRoutes() {
        return Collections.unmodifiableList(routes);
    }

    /**
     * Add new route
     */
    public void addRoute(Route route) {
        routes.add(route);
        routes.sort(Comparator.comparingInt(Route::getPriority));
        log.info("Added route: {} -> {}", route.getPath(), route.getDestinationUrl());
    }

    /**
     * Update existing route
     */
    public void updateRoute(String routeId, Route updatedRoute) {
        routes.stream()
                .filter(r -> r.getId().equals(routeId))
                .findFirst()
                .ifPresent(route -> {
                    route.setPath(updatedRoute.getPath());
                    route.setDestinationUrl(updatedRoute.getDestinationUrl());
                    route.setEnabled(updatedRoute.isEnabled());
                    route.setPriority(updatedRoute.getPriority());
                    routes.sort(Comparator.comparingInt(Route::getPriority));
                    log.info("Updated route: {}", routeId);
                });
    }

    /**
     * Remove route
     */
    public void removeRoute(String routeId) {
        routes.removeIf(r -> r.getId().equals(routeId));
        log.info("Removed route: {}", routeId);
    }

    public void setRoutes(List<Route> routes) {
        this.routes = routes;
    }

    public List<Route> getRoutes() {
        return routes;
    }
}
