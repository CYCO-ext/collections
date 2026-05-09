package org.example.application.port.out;

import org.example.application.route.RouteModels.RouteOptimizationProblem;
import org.example.application.route.RouteModels.RouteOptimizationResult;

public interface RouteOptimizationPort {
    RouteOptimizationResult optimize(RouteOptimizationProblem problem);
}
