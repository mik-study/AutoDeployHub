package com.proj.autodeploy.deployment.pipeline;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * 외부 프로세스 실행 추상화. (git / docker CLI)
 *
 * <p>인터페이스로 둔 이유는 테스트 때문이다. Worker 파이프라인 테스트가 실제 docker 데몬을 요구하면
 * CI 는 물론 로컬에서도 돌릴 수 없다. {@link FakeCommandRunner} 로 갈아끼워 파이프라인의
 * <b>상태 전이·로그·실패 처리</b>를 검증한다.
 *
 * <p>출력은 한 줄씩 콜백으로 흘려보낸다. 전체를 모아서 반환하면 {@code docker build} 처럼
 * 수 분 걸리는 작업의 진행 상황을 실시간 로그로 내보낼 수 없다.
 */
public interface CommandRunner {

    /**
     * 명령을 실행하고 종료 코드를 돌려준다.
     *
     * @param workingDirectory 작업 디렉터리 (null 이면 프로세스 기본값)
     * @param command          실행할 명령과 인자. 셸을 거치지 않으므로 인자를 직접 쪼개서 넘긴다
     *                         (셸 해석이 없으니 값에 공백·따옴표가 있어도 인젝션이 되지 않는다)
     * @param timeout          초과 시 프로세스를 죽이고 {@link CommandTimeoutException}
     * @param outputLine       stdout/stderr 한 줄마다 호출되는 콜백
     * @return 프로세스 종료 코드
     */
    int run(Path workingDirectory, List<String> command, Duration timeout, Consumer<String> outputLine);

    /** 타임아웃으로 강제 종료됐을 때. */
    class CommandTimeoutException extends RuntimeException {
        public CommandTimeoutException(String message) {
            super(message);
        }
    }
}
