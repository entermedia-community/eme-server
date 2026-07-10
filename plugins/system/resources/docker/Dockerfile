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

#RUN mkdir -p /usr/share/ && wget --output-document=/usr/share/eme-lib.tar.gz --no-cache -q http://192.229.4.120:8080/eme-lib.tar.gz && \
#RUN  mkdir -p /usr/share/ && wget --output-document=/usr/share/eme-lib.tar.gz --no-cache -q https://dev.entermediadb.org/jenkins/view/EM12/job/eme-lib/lastSuccessfulBuild/artifact/deploy/eme-lib.tar.gz && \
#   cd /usr/share/ && tar -xzf eme-lib.tar.gz && rm -rf eme-lib.tar.gz 

RUN wget -O - https://raw.githubusercontent.com/entermedia-community/eme-server/refs/heads/main/plugins/system/resources/bin/eme.sh >> /usr/bin/eme  && chmod +x /usr/bin/eme
COPY delegates.xml /opt/imei/share/ImageMagick-7/delegates.xml
COPY sysctl.conf /etc/sysctl.conf
COPY limits.conf /etc/security/limits.conf
COPY nproc.conf /etc/security/limits.d/20-nproc.conf

#RUN rm -rf /tmp/* && \
#    ln -s /usr/share/eme-lib/resources/bin/eme.sh /usr/bin/eme && \
#    chmod +x /usr/bin/eme

#remove user with id 1000 and group with id 1000 to avoid conflicts with host user when running with --user
RUN userdel -f $(getent passwd 1000 | cut -d: -f1) 2>/dev/null || true && \
    groupdel -f $(getent group 1000 | cut -d: -f1) 2>/dev/null || true

CMD ["/usr/bin/bash"]

#CMD ["/usr/bin/eme", "start", "/usr/share/eme-instance" ] 