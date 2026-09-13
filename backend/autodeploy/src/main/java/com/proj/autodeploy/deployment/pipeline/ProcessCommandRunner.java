package com.proj.autodeploy.deployment.pipeline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link ProcessBuilder} 로 git/docker CLI 를 호출하는 기본 구현.
 *
 * <p><b>docker-java 대신 CLI 를 고른 이유</b> (과제 3 가이드 §3 의 "모임에서 확정" 항목):
 * <ul>
 *   <li>git clone 과 docker 를 <b>같은 방식</b>으로 다룰 수 있다. docker-java 를 써도 git 은
 *       어차피 CLI 나 JGit 이 필요해서 실행 경로가 둘로 갈린다</li>
 *   <li>의존성이 늘지 않는다. MVP 에서 쓰는 건 build/run/inspect 몇 개뿐이다</li>
 *   <li>가이드 §6 폴백({@code DockerClient} 추상화 + mock)이 요구하는 seam 은
 *       {@link CommandRunner} 로 이미 확보된다</li>
 * </ul>
 * 빌드 로그 콜백 스트리밍도 {@code outputLine} 으로 동일하게 얻는다. 이미지 레이어 단위 제어나
 * 빌드 취소가 필요해지면 그때 docker-java 로 교체한다.
 *
 * <p>stderr 를 stdout 으로 합친다({@code redirectErrorStream}). docker build 는 진행 상황을
 * stderr 로 뿜기 때문에, 나누면 배포 로그의 시간 순서가 뒤섞인다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "autodeploy.pipeline.mode", havingValue = "real", matchIfMissing = true)
public class ProcessCommandRunner implements CommandRunner {

    @Override
    public int run(Path workingDirectory, List<String> command, Duration timeout,
                   Consumer<String> outputLine) {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }

        Process process = null;
        try {
            process = builder.start();

            // 출력을 읽지 않으면 파이프 버퍼가 차면서 자식 프로세스가 멈춘다 - 반드시 소비해야 한다.
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLine.accept(line);
                }
            }

            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new CommandTimeoutException(
                        "command timed out after " + timeout.toSeconds() + "s: " + String.join(" ", command));
            }
            return process.exitValue();

        } catch (IOException e) {
            throw new IllegalStateException("failed to run command: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw new IllegalStateException("interrupted while running: " + String.join(" ", command), e);
        }
    }
}
