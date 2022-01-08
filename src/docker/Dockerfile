# Copyright 2020-2022 Chi Cao Minh
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

ARG INTELLIJ_VERSION=2021.3

# For selecting patch versions, if available
ARG INTELLIJ_FULL_VERSION=${INTELLIJ_VERSION}.1

# Set via build arg to run IntelliJ with GUI (--build-arg TARGET=dev)
ARG TARGET=prod

ARG USER=inspect
ARG HOME=/home/${USER}
ARG TMPDIR=/tmp/scratch
ARG INTELLIJ_TMPDIR=${TMPDIR}/intellij

ARG OPENJDK=adoptopenjdk/openjdk11:jdk-11.0.11_9-alpine-slim

# NOTE: If a build stage name appears multiple times, it must be lowercase:
# https://github.com/moby/buildkit/issues/2003. For consistency, even if a build
# stage name appears only once is this Dockerfile, it is also lowercase.


# Make sure the alpine version matches the "OPENJDK" image above
FROM alpine:3.14 AS stage_setup

ARG INSPECTOR_VERSION
ARG INTELLIJ_FULL_VERSION
ARG INTELLIJ_TMPDIR

SHELL ["/bin/ash", "-eo", "pipefail", "-c"]

RUN apk --no-cache add \
  curl=7.79.1-r0 \
  tar=1.34-r0 \
  unzip=6.0-r9

WORKDIR ${INTELLIJ_TMPDIR}
RUN mkdir -p ${INTELLIJ_TMPDIR} \
  && curl -fsLS https://download.jetbrains.com/idea/ideaIC-${INTELLIJ_FULL_VERSION}-no-jbr.tar.gz \
    | tar --strip-components 1 -xz


# hadolint ignore=DL3006
FROM ${OPENJDK} AS stage_build

ARG TMPDIR

WORKDIR ${TMPDIR}
COPY . ${TMPDIR}
RUN ./gradlew jar


# hadolint ignore=DL3006
FROM ${OPENJDK} AS stage_prod

ARG HOME
ARG TMPDIR
ARG INTELLIJ_VERSION
ARG USER

ARG GROUP=${USER}

# IntelliJ needs a font to run.
#
# Use uid/gid of 2000 to match the values for the "travis" user in Travis CI.
#
# For some reason, without an .m2 directory, IntelliJ 2020+ hangs when
# inspecting an opened maven project.
RUN apk --no-cache add font-noto=0_git20190623-r2 \
  && addgroup -g 2000 ${GROUP} \
  && adduser -u 2000 -G ${GROUP} -s /bin/sh -D ${USER} \
  && mkdir ${HOME}/.m2
COPY --from=stage_setup --chown=${USER}:${GROUP} ${TMPDIR} ${HOME}
COPY --chown=${USER}:${GROUP} src/docker/jdk.table.xml ${HOME}/.IdeaIC${INTELLIJ_VERSION}/config/options/
COPY --chown=${USER}:${GROUP} --from=stage_build ${TMPDIR}/build/libs/intellij-inspect.jar ${HOME}


FROM stage_prod AS stage_dev

ARG HOME
ARG INTELLIJ_VERSION

# Need X11 libs to run IntelliJ with GUI. Also, since we want to generate a new
# jdk.table.xml when using GUI, remove the existing one.
RUN apk --no-cache add \
    libxext=1.3.4-r0 \
    libxi=1.7.10-r0 \
    libxrender=0.9.10-r3 \
    libxtst=1.2.3-r3 \
  && rm -rf "${HOME}/.IdeaIC${INTELLIJ_VERSION}"


# The TARGET build arg is used to achieve conditional builds via different build
# stages. For docker build caching, using different build stages turns out to
# work better than adding conditionals within RUN commands.
#
# hadolint ignore=DL3006
FROM stage_${TARGET} AS stage_final

WORKDIR ${HOME}
USER ${USER}
# By default, keep inspection results inside container
ENTRYPOINT [ \
  "java", \
  "-jar", "intellij-inspect.jar", \
  "--output", "/home/inspect/inspection-results", \
  "/home/inspect/intellij" \
]
CMD [ "--help" ]
