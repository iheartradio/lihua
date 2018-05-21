[![Build Status](https://travis-ci.org/iheartradio/lihua.svg?branch=master)](https://travis-ci.org/iheartradio/lihua

### WIP

#### Principled NoSql DB Access Layer


Lihua supports encrypted password passed in through config. It provides some tools for encryption. 

To generate a secret key
```
sbt mongo/run genKey
```

To encrypt a password
```
sbt mongo/run encrypt
```
