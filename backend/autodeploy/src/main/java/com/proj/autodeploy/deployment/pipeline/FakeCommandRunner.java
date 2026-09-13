package com.proj.autodeploy.deployment.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * git/docker 없이 파이프라인을 끝까지 굴리는 폴백 구현. (과제 3 가이드 §6 DoD 안전장치)
 *
 * <p>{@code autodeploy.pipeline.mode=mock} 일 때 {@link ProcessCommandRunner} 대신 등록된다.
 * 용도는 둘이다.
 * <ul>
 *   <li>민준의 docker socket 마운트 작업이 끝나기 전에 <b>상태머신 + SSE E2E 를 먼저 그린화</b></li>
 *   <li>docker 데몬이 없는 환경(CI, 리뷰어 로컬)에서 데모</li>
 * </ul>
 *
 * <p>clone 명령일 때는 워크스페이스에 Dockerfile 을 실제로 만들어 둔다. 그래야 다음 단계의
 * Dockerfile 검사가 실제 파일시스템을 보고 통과한다 — 검사 로직까지 mock 으로 우회하면
 * 정작 검증하려는 경로가 비어버린다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "autodeploy.pipeline.mode", havingValue = "mock")
public class FakeCommandRunner implements CommandRunner {

    @Override
    public int run(Path workingDirectory, List<String> command, Duration timeout,
                   Consumer<String> outputLine) {
        String joined = String.join(" ", command);
        outputLine.accept("[mock] $ " + joined);

        if (command.contains("clone")) {
            // 실제 git clone 과 마찬가지로 "마지막 인자 = 대상 디렉터리" 에 만든다.
            // workingDirectory 에 만들면 clone 대상이 아니라 그 부모에 파일이 생긴다.
            createFakeWorkspace(Path.of(command.getLast()), outputLine);
        } else if (command.contains("rev-parse")) {
            outputLine.accept("0123456789abcdef0123456789abcdef01234567");
        } else if (command.contains("build")) {
            outputLine.accept("[mock] Step 1/3 : FROM eclipse-temurin:21-jre");
            outputLine.accept("[mock] Step 2/3 : COPY app.jar /app.jar");
            outputLine.accept("[mock] Step 3/3 : ENTRYPOINT [\"java\",\"-jar\",\"/app.jar\"]");
            outputLine.accept("[mock] Successfully built image");
        } else if (command.contains("run")) {
            outputLine.accept("[mock] container started");
        }
        return 0;
    }

    private void createFakeWorkspace(Path target, Consumer<String> outputLine) {
        // git clone 은 대상 디렉터리를 스스로 만든다. mock 도 같은 결과를 만들어줘야
        // 뒤 단계가 "디렉터리 없음"으로 실패하지 않는다.
        try {
            Files.createDirectories(target);
            Files.writeString(target.resolve("Dockerfile"),
                    "FROM eclipse-temurin:21-jre\nENTRYPOINT [\"java\",\"-version\"]\n");
            outputLine.accept("[mock] cloned repository (Dockerfile generated)");
        } catch (Exception e) {
            throw new IllegalStateException("failed to create mock workspace at " + target, e);
        }
    }
}
