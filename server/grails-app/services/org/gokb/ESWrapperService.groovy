package org.gokb


import groovy.json.JsonSlurper
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.impl.client.BasicCredentialsProvider
import org.opensearch.client.RestClient
import org.opensearch.client.RestClientBuilder
import org.opensearch.client.RestHighLevelClient

import static groovy.json.JsonOutput.*

class ESWrapperService {

  static transactional = false
  def grailsApplication
  RestHighLevelClient esClient

  static Map indicesPerType = [
      "JournalInstance" : "titles",
      "DatabaseInstance" : "titles",
      "OtherInstance" : "titles",
      "BookInstance" : "titles",
      "TitleInstance" : "titles",
      "TitleInstancePackagePlatform" : "tipps",
      "Org" : "orgs",
      "Package" : "packages",
      "Platform" : "platforms"
  ]

  @javax.annotation.PostConstruct
  def init() {
    log.debug("Init Elasticsearch wrapper service...")
  }


  def getSettings() {
    InputStream settingsStream = getClass().getResourceAsStream('/elasticsearch/es_settings.json')
    return new JsonSlurper().parse(settingsStream)
  }


  def getMapping() {
    InputStream mappingStream = getClass().getResourceAsStream('/elasticsearch/es_mapping.json')
    return new JsonSlurper().parse(mappingStream)
  }


  private void newClient() {
    def es_host_name = grailsApplication.config.getProperty('gokb.es.host')
    def es_port = grailsApplication.config.getProperty('gokb.es.ports', Collection).get(0) ?: 9200
    def es_scheme = grailsApplication.config.getProperty('gokb.es.scheme', String, 'http')
    def es_username = grailsApplication.config.getProperty('gokb.es.username')
    def es_password = grailsApplication.config.getProperty('gokb.es.password')

    log.debug("Elasticsearch client is null, creating now... host: ${es_host_name}")
    log.debug("... looking for Elasticsearch on host ${es_host_name}:${es_port} with scheme ${es_scheme}")

    HttpHost httpHost = new HttpHost(es_host_name, es_port, es_scheme)
    RestClientBuilder builder = RestClient.builder(httpHost)

    // Add authentication if username and password are provided
    if (es_username && es_password) {
      log.debug("... configuring Elasticsearch authentication for user: ${es_username}")

      final CredentialsProvider credentialsProvider = new BasicCredentialsProvider()
      credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(es_username, es_password)
      )

      builder.setHttpClientConfigCallback { httpClientBuilder ->
        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
      }
    } else {
      log.debug("... no Elasticsearch authentication configured")
    }

    esClient = new RestHighLevelClient(builder)
    log.debug("... Elasticsearch wrapper service init completed")
  }


  def index(index,typename,record_id, record) {
    log.debug("Indexing ... type: ${typename}, id: ${record_id}...")
    def result=null
    try {
      def future = getClient().prepareIndex(index,typename,record_id).setSource(record)
      result=future.get()
    }
    catch ( Exception e ) {
      log.error("Error processing ${toJson(record)}", e)
      e.printStackTrace()
    }
    log.debug("... indexing complete")
    result
  }


  def getClient() {
    if (!esClient) {
      newClient()
    }
    esClient
  }


  @javax.annotation.PreDestroy
  def destroy() {
    try {
      esClient?.close()
    }
    catch (Exception e) {
      log.error("Problem occurred closing Elasticsearch client", e)
    }
  }

}
