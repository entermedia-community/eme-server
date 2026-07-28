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
COPY delegates.xml /opt/imei/share/ImageMagick-7/delegates.xml
COPY sysctl.conf /etc/sysctl.conf
COPY limits.conf /etc/security/limits.conf
COPY nproc.conf /etc/security/limits.d/20-nproc.conf

ARG CACHE_BREAKER
RUN mkdir -p /home/entermedia/eme-server/bin && wget -O - https://raw.githubusercontent.com/entermedia-community/eme-server/refs/heads/main/bin/eme.sh >> /home/entermedia/eme-server/bin/eme.sh && chmod +x /home/entermedia/eme-server/bin/eme.sh && ln -s /home/entermedia/eme-server/bin/eme.sh /usr/bin/eme


#remove user with id 1000 and group with id 1000 to avoid conflicts with host user when running with --user
RUN userdel -f $(getent passwd 1000 | cut -d: -f1) 2>/dev/null || true && \
    groupdel -f $(getent group 1000 | cut -d: -f1) 2>/dev/null || true

#default behavior is run bash but the run command can be overridden to run the eme start command
CMD ["/usr/bin/bash"]

#CMD ["/usr/bin/eme", "start", "/usr/share/eme-instance" ] 