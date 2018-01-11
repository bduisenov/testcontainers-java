package org.testcontainers.dockerclient;

import com.github.dockerjava.core.DockerClientConfig;
import com.google.common.net.InetAddresses;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.DockerClientFactory;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DockerClientConfigUtils {

    // See https://github.com/docker/docker/blob/a9fa38b1edf30b23cae3eade0be48b3d4b1de14b/daemon/initlayer/setup_unix.go#L25
    public static final boolean IN_A_CONTAINER = new File("/.dockerenv").exists();

    @Getter(lazy = true)
    private static final Optional<String> detectedDockerHostIp = Optional
            .of(IN_A_CONTAINER)
            .filter(it -> it)
            .map(file -> DockerClientFactory.instance().runInsideDocker(
                    cmd -> cmd.withCmd("ip", "route"),
                    (client, id) -> {
                        try {
                            return Unreliables.retryUntilSuccess(3, TimeUnit.SECONDS, () -> {
                                String output = client.logContainerCmd(id)
                                        .withStdOut(true)
                                        .withSince(0)
                                        .exec(new LogToStringContainerCallback())
                                        .toString();

                                output = StringUtils.trimToEmpty(output);

                                for (String line : output.split("\n")) {
                                    if (line.startsWith("default")) {
                                        for (String part : line.split(" ")) {
                                            part = StringUtils.trimToEmpty(part);
                                            if (InetAddresses.isInetAddress(part)) {
                                                return part;
                                            }
                                        }
                                    }
                                }

                                String message = "'ip route' did not contain a default route. Output:\n" + output;
                                log.warn(message);

                                throw new IllegalStateException(message);
                            });
                        } catch (Exception e) {
                            log.warn("Can't parse the default gateway IP", e);
                            return null;
                        }
                    }
            ))
            .map(StringUtils::trimToEmpty)
            .filter(StringUtils::isNotBlank);

    public static String getDockerHostIpAddress(DockerClientConfig config) {
        return getDetectedDockerHostIp().orElseGet(() -> {
            switch (config.getDockerHost().getScheme()) {
                case "http":
                case "https":
                case "tcp":
                    return config.getDockerHost().getHost();
                case "unix":
                    return "localhost";
                default:
                    return null;
            }
        });
    }
}
