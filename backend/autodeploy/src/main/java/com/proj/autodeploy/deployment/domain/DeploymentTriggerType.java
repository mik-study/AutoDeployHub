package com.proj.autodeploy.deployment.domain;

public enum DeploymentTriggerType {
    MANUAL,    // 사용자가 UI 에서 버튼 클릭
    WEBHOOK,   // GitHub push 이벤트
    ROLLBACK   // 명시적 rollback 요청으로 생성된 deployment
}
