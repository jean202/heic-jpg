package io.github.jean202.heicjpg;

import java.util.List;

record ConversionPlan(List<ConversionTask> tasks, List<String> errors) {
}
