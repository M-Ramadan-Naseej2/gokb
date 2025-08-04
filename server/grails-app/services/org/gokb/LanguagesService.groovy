package org.gokb

import grails.gorm.transactions.Transactional

import groovy.json.JsonSlurper

import io.micronaut.http.client.*

import org.gokb.cred.KBComponent
import org.gokb.cred.RefdataCategory

@Transactional
class LanguagesService{

  def grailsApplication

  static Map languages = [:]

  @Transactional
  public void initialize(){
    InputStream languageStream = getClass().getResourceAsStream('/languages/languages.json')
    languages = new JsonSlurper().parse(languageStream)

    for (def entry in languages){
      RefdataCategory.lookupOrCreate(KBComponent.RD_LANGUAGE, entry.key, entry.key)
    }
  }

  Map getLanguages(){
    if (!languages) {
      initialize()
    }

    languages
  }

}
