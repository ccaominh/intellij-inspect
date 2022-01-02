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

ARG INTELLIJ_VERSION=2018.3
ARG INTELLIJ_FULL_VERSION=${INTELLIJ_VERSION}.6

ARG USER="inspect"
ARG HOME=/home/${USER}
ARG TMPDIR=/tmp/scratch
ARG INTELLIJ_TMPDIR=${TMPDIR}/intellij


FROM alpine:3.14 AS SETUP

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
  && curl -fsLS https://download.jetbrains.com/idea/ideaIC-${INTELLIJ_FULL_VERSION}-no-jdk.tar.gz \
    | tar --strip-components 1 -xz


FROM adoptopenjdk/openjdk8:jdk8u292-b10-alpine-slim AS BUILD

ARG TMPDIR

WORKDIR ${TMPDIR}
COPY . ${TMPDIR}
RUN ./gradlew jar


FROM adoptopenjdk/openjdk8:jdk8u292-b10-alpine-slim AS FINAL

ARG HOME
ARG TMPDIR
ARG INTELLIJ_VERSION
ARG USER

ARG GROUP=${USER}

RUN apk --no-cache add font-noto=0_git20190623-r2
# Use uid/gid of 2000 to match the values for the "travis" user in Travis CI
RUN addgroup -g 2000 ${GROUP} && adduser -u 2000 -G ${GROUP} -s /bin/sh -D ${USER}
COPY --from=SETUP --chown=${USER}:${GROUP} ${TMPDIR} ${HOME}
COPY --chown=${USER}:${GROUP} src/docker/jdk.table.xml ${HOME}/.IdeaIC${INTELLIJ_VERSION}/config/options/
COPY --chown=${USER}:${GROUP} --from=BUILD ${TMPDIR}/build/libs/intellij-inspect.jar ${HOME}

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
