FROM gradle:jdk23-alpine AS BUILDER
WORKDIR /opt/build/btree
COPY --chown=gradle:gradle . /opt/build/btree
RUN gradle build --no-daemon -x test

FROM eclipse-temurin:23-jdk-alpine
WORKDIR /opt/app
COPY --from=BUILDER /opt/build/btree/build/libs/distributed-b-tree-1.0-SNAPSHOT.jar /opt/app/btree.jar

EXPOSE 8080 8090

ENTRYPOINT ["java", "-jar", "/opt/app/btree.jar"]