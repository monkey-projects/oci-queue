# OCI Queue API

This is a Clojure library that allows you to access the [Oracle Cloud Queue
API](https://docs.oracle.com/en-us/iaas/Content/queue/overview.htm#overview).

It uses [Martian](https://github.com/oliyh/martian) to map routing configuration
to actual functions.

## Usage

Include the lib in your project.
For CLI tools (`deps.edn`):
```clojure
{:com.monkeyprojects/oci-queue {:mvn/version "<version>"}}
```
Or Leiningen:
```clojure
[com.monkeyprojects/oci-queue "<version>"]
```

The functions are in the `monkey.oci.queue.core` namespace.  First of all, you'll
have to create a context.  This must be passed to all functions subsequently.
The config needed to create the context should include all fields necessary
to access the API.  The private key must be a Java PrivateKey object.  You can
parse one using `monkey.oci.common.utils/load-privkey`.

```clojure
(require '[monkey.oci.queue.core :as fc])
(require '[monkey.oci.common.utils :as u])

;; The config is required to access the OCI API
(def config {:user-ocid "my-user-ocid"
             :tenancy-ocid "my-tenancy"
	     :key-fingerprint "some fingerprint"
	     :region "eu-frankfurt-1"
	     :private-key (u/load-privkey "my-key-file")})
	     

(def ctx (fc/make-context config))

;; Do some stuff
@(fc/list-queues ctx {})
; => Functions return a deferred so deref it
```

The functions return the raw response from the API, where the body is parsed from JSON.
This is because sometimes you'll need the headers, e.g. for pagination.  Mostly you'll
just need the `:body` and `:status`.  Some higher-order functions will be provided
later on to allow you to work with these values.

### Available Endpoints

The endpoints are auto-generated from the routes declared in the [core namespace](src/monkey/oci/queue/core.clj)
and they reflect those declared in the [Queue Api documentation](https://docs.oracle.com/en-us/iaas/api/#/en/queue/20210201/).

## License

MIT license, see [LICENSE](LICENSE).
Copyright (c) 2023 by [Monkey Projects BV](https://www.monkeyprojects.be).
