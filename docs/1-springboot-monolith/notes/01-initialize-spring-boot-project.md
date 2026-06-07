# 1 Initialize spring boot project
> This guide is OS specific for Arch Linux, as this project is developed on this os
> It is perfectly possible to execute the same task on a different OS, but instructions will not be provided.
> These guide assumes you have an internet connection pacman and yay working and installed. 

### Tools and libraries
##### Java
Java [official doc](https://wiki.archlinux.org/title/Java)
This project uses openjdk 21
1. install
    ```Bash
    sudo pacman -S jdk21-openjdk
    ```
2. verfy the installation:
    ```Bash
    java -version
    ```
    output should look like:
    ```Bash
    $ java -version
    openjdk version "21.0.11" 2026-04-21
    OpenJDK Runtime Environment (build 21.0.11+10)
    OpenJDK 64-Bit Server VM (build 21.0.11+10, mixed mode, sharing)
    ``` 

##### Maven
Maven [arch wiki](https://wiki.archlinux.org/title/Maven)
This project uses Apache Maven 3.9.16
1. install:
    ```Bash
    sudo pacman -S maven
    ``` 
2. verify the installation:
    ```Bash
    java -version
    ```
    output should look like:
    ```Bash
    Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
    Maven home: /usr/share/java/maven
    Java version: 21.0.11, vendor: Arch Linux, runtime: /usr/lib/jvm/java-21-openjdk
    Default locale: en_US, platform encoding: UTF-8
    OS name: "linux", version: "6.18.33-1-lts", arch: "amd64", family: "unix"
    ``` 
3. initialize the project:

##### Spring cli
Spring [aur repo](https://aur.archlinux.org/packages/spring-boot-cli)
This project uses Spring CLI v3.2.0
1. install:
    ```Bash
    yay -S spring-boot-cli
    ``` 
2. verfy the installation:
    ```Bash
    spring --version
    ``` 
    output should look like:
    ```Bash
    $ spring --version
    Spring CLI v3.2.0
    ``` 
3. create the project with spring init
    ```Bash 
    spring init \
      --build=maven \
      --java-version=21 \
      --group-id=ch.supsi.imageprocessing \
      --artifact-id=cloud-native-image-processing \
      --package-name=ch.supsi.imageprocessing \
      --dependencies=web,data-jpa,data-rest,postgresql,hal-explorer \
      cloud-native-image-processing
    ```
    you should now see these folders and files (example with tree command):
    ```Bash
    .
    ├── cloud-native-image-processing
    │   ├── HELP.md
    │   ├── mvnw
    │   ├── mvnw.cmd
    │   ├── pom.xml
    │   └── src
    │       ├── main
    │       │   ├── java
    │       │   │   └── ch
    │       │   │       └── supsi
    │       │   │           └── imageprocessing
    │       │   │               └── CloudNativeImageProcessingApplication.java
    │       │   └── resources
    │       │       ├── application.properties
    │       │       ├── static
    │       │       └── templates
    │       └── test
    │           └── java
    │               └── ch
    │                   └── supsi
    │                       └── imageprocessing
    │                           └── CloudNativeImageProcessingApplicationTests.java
    ├── docs
    │   └── 1-initialize-spring-boot-project.md
    └── README.md
    
    17 directories, 9 files
    ```
5. explore dependecies:
    open the pom.xml and look for <dependencies>, for example this is the hal explorer dependency, you should see all the previously listed dependency in the spring init command.
    ```Bash
    <dependency>
        <groupId>org.springframework.data</groupId>
        <artifactId>spring-data-rest-hal-explorer</artifactId>
    </dependency>   
    ```
5. try to run the project:
    (it will fail if you havent configured a db yet, PostgreSQL config will be done in task 2)
