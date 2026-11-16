package com.workbench.workshop.domain.monitoring;

import java.time.Duration;

/** Unidade de tempo do relatório de tempo médio de execução (paridade com o monólito). */
public enum AverageTimeEnum {
    SECONDS {
        @Override
        public double calculate(Duration duration) {
            return duration.toSeconds();
        }
    },
    MINUTES {
        @Override
        public double calculate(Duration duration) {
            return duration.toMillis() / 60000.0;
        }
    },
    HOURS {
        @Override
        public double calculate(Duration duration) {
            return duration.toMillis() / 3600000.0;
        }
    };

    public abstract double calculate(Duration duration);
}
