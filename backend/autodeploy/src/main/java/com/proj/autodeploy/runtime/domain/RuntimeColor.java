package com.proj.autodeploy.runtime.domain;

/**
 * Blue-Green 무중단 배포의 색. (02_decisions.md §2.4, 04_state_machine.md §6)
 */
public enum RuntimeColor {
    BLUE,   // 기본 port 8081
    GREEN;  // 기본 port 8082

    public RuntimeColor opposite() {
        return this == BLUE ? GREEN : BLUE;
    }

    public int defaultPort() {
        return this == BLUE ? 8081 : 8082;
    }
}
