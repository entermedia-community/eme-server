FROM entermediadb/emubuntu:eme
LABEL maintainer="EnterMedia <help@entermediadb.org>"

#Environment
ENV CLIENT_NAME=entermedia
ENV INSTANCE_PORT=8080
ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8
ENV JAVA_HOME=/usr/lib/jvm/default-java

ARG CACHE_BREAKER=1
##Make sure: ln -s deploy/builds/eme-lib-12.\$\{env.BUILD_NUMBER\}.tar.gz resources/docker/local/eme-lib.tar.gz
##RUN  mkdir -p /usr/share/ && wget --output-document=/usr/share/eme-lib.tar.gz --no-cache -q https://dev.entermediadb.org/jenkins/view/EM12/job/eme-lib/lastSuccessfulBuild/artifact/deploy/eme-lib.tar.gz
#COPY eme-lib.tar.gz /usr/share/eme-lib.tar.gz
#RUN  cd /usr/share/ && tar -xzf eme-lib.tar.gz && rm -rf eme-lib.tar.gz 

COPY resources/delegates.xml /opt/imei/share/ImageMagick-7/delegates.xml
COPY resources/sysctl.conf /etc/sysctl.conf
COPY resources/limits.conf /etc/security/limits.conf
COPY resources/nproc.conf /etc/security/limits.d/20-nproc.conf

#RUN cp /usr/share/eme-lib/resources/docker/delegates.xml /opt/imei/share/ImageMagick-7/delegates.xml && \
#    cp /usr/share/eme-lib/resources/docker/sysctl.conf /etc/sysctl.conf && \
#    cp /usr/share/eme-lib/resources/docker/limits.conf /etc/security/limits.conf && \
#    cp /usr/share/eme-lib/resources/docker/nproc.conf /etc/security/limits.d/20-nproc.conf

#RUN rm -rf /tmp/* && \
#    ln -s /usr/share/eme-lib/resources/bin/eme.sh /usr/bin/eme && \
#    chmod +x /usr/bin/eme

CMD ["/usr/bin/bash"]

