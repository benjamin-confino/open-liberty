# This private impl feature corresponds to JSON-B 3.0 with the Yasson implementation
-include= ~${workspace}/cnf/resources/bnd/feature.props
symbolicName=io.openliberty.jsonbImpl-3.0.0
singleton=true
visibility=private
-features=com.ibm.websphere.appserver.eeCompatible-10.0, \
  com.ibm.websphere.appserver.bells-1.0, \
  io.openliberty.jakarta.cdi-4.0, \
  io.openliberty.jsonp-2.1
-bundles=\
  io.openliberty.jakarta.jsonb.3.0; location:="dev/api/spec/,lib/"; mavenCoordinates="io.openliberty.jakarta.json.bind:jakarta.json.bind-api:3.0.0"
kind=noship
edition=full
WLP-Activation-Type: parallel
