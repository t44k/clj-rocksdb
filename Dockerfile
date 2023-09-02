FROM clojure:temurin-17-lein-alpine@sha256:ec6d5153a609132ffc6e4939bd848e44f4bbb239737f498ae76d476b9a03918b

RUN apk add --no-cache libstdc++

RUN addgroup -g 1000 -S clj && adduser -u 1000 -S clj -G clj --disabled-password

WORKDIR /work

USER clj