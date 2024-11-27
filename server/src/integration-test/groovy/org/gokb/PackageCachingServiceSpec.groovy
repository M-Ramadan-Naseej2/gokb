import com.k_int.ConcurrencyManagerService

import gokbg3.DateFormatService

import grails.testing.mixin.integration.Integration
import grails.testing.services.ServiceUnitTest

import java.time.LocalDateTime

import org.gokb.*
import org.gokb.cred.BookInstance
import org.gokb.cred.Identifier
import org.gokb.cred.IdentifierNamespace
import org.gokb.cred.JournalInstance
import org.gokb.cred.KBComponent
import org.gokb.cred.Org
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.RefdataCategory
import org.gokb.cred.ReviewRequest
import org.gokb.cred.TitleInstance
import org.gokb.cred.TitleInstancePackagePlatform
import org.gokb.cred.Combo
import org.hibernate.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Rollback
import org.springframework.transaction.annotation.Transactional

import spock.lang.Specification

@Integration
@Transactional
@Rollback
class PackageCachingServiceSpec extends Specification {

  @Autowired
  PackageCachingService packageCachingService

  @Autowired
  PackageService packageService

  @Autowired
  TippUpsertService tippUpsertService

  @Autowired
  DateFormatService dateFormatService

  @Autowired
  SessionFactory sessionFactory

  @Autowired
  ConcurrencyManagerService concurrencyManagerService

  IdentifierNamespace issn_ns
  IdentifierNamespace eissn_ns
  IdentifierNamespace isbn_ns

  Package testPkg
  Platform testPlt
  Org testOrg

  def setup() {
    filePath = packageService.exportFilePath()

    testOrg = Org.findByName('PackageService Test Org') ?: new Org(name: 'PackageService Test Org').save(flush: true)
    testPlt = Platform.findByName('PackageService Test Platform') ?: new Platform(name: 'PackageService Test Platform', provider: testOrg).save(flush: true)
    testPkg = Package.findByName('PackageService Test Package') ?: new Package(name: 'PackageService Test Package', provider: testOrg).save(flush: true)

    if (!issn_ns) {
      issn_ns = IdentifierNamespace.findByValue('issn')
    }
    if (!eissn_ns) {
      eissn_ns = IdentifierNamespace.findByValue('eissn')
    }
    if (!isbn_ns) {
      isbn_ns = IdentifierNamespace.findByValue('isbn')
    }

    if (!BookInstance.findByName('PackageService Book 1')) {
      Identifier isbn = new Identifier(namespace: isbn_ns, value: '979-11-655-6390-5').save(flush: true)
      BookInstance book = new BookInstance(name: 'PackageService Book 1').save(flush:true)
      book.ids.add(isbn)
      book.save(flush: true)

      def tipp_map = [
        pkg: testPkg.id,
        hostPlatform: testPlt.id,
        name: 'PackageService Book 1',
        url: 'https://package-caching-test.test/book1',
        editStatus: 'Approved',
        publicationType: 'Monograph',
        importId: 'pcsB1',
        firstAuthor: 'Author1',
        firstEditor: 'Editor1',
        volumeNumber: '1',
        editionStatement: '1st ed',
        hybridOA: 'No',
        paymentType: 'Unknown',
        accessStartDate: '2020-01-01',
        accessEndDate: '2030-12-31',
        subjectArea: 'Subject1',
        series: 'Series1',
        publisherName: 'PackageService Test Org',
        dateFirstInPrint: '2001-01-01',
        dateFirstOnline: '2019-01-01',
        medium: 'Book',
        coverage: [
          [
            coverageDepth: 'fulltext',
            startDate: '',
            startVolume: '',
            startIssue: '',
            endDate: '',
            endVolume: '',
            endIssue: '',
            coverageNote: ''
          ]
        ]
      ]

      TitleInstancePackagePlatform tipp = tippUpsertService.upsertDTO(tipp_map)


      tipp.title = book
      tipp.ids.add(isbn)
      tipp.save(flush: true)
    }

    if (!JournalInstance.findByName('PackageService Journal 1')) {
      Identifier issn = Identifier.findByNamespaceAndValue(issn_ns, '0128-5483') ?: new Identifier(namespace: issn_ns, value: '0128-5483')
      Identifier eissn = Identifier.findByNamespaceAndValue(eissn_ns, '2180-4338') ?: new Identifier(namespace: eissn_ns, value: '2180-4338')
      Identifier eissn2 = Identifier.findByNamespaceAndValue(eissn_ns, '1727-9445') ?: new Identifier(namespace: eissn_ns, value: '1727-9445')

      JournalInstance journal = new JournalInstance(name: 'PackageService Journal 1').save(flush:true)
      journal.ids.addAll([issn, eissn])
      journal.save(flush: true)

      JournalInstance journal2 = new JournalInstance(name: 'PackageService Journal 2').save(flush:true)
      journal2.ids.addAll([issn, eissn2])
      journal2.save(flush: true)

      def tipp1_map = [
        pkg: testPkg.id,
        hostPlatform: testPlt.id,
        name: 'PackageService Journal 1',
        url: 'https://package-caching-test.test/journal1',
        editStatus: 'Approved',
        publicationType: 'Serial',
        importId: 'pcsJ1',
        hybridOA: 'No',
        paymentType: 'Unknown',
        accessStartDate: '2020-01-01',
        accessEndDate: '',
        subjectArea: 'Subject1',
        series: 'Series1',
        publisherName: 'PackageService Test Org',
        medium: 'Book',
        coverage: [
          [
            coverageDepth: 'fulltext',
            startDate: '2020-01',
            startVolume: '1',
            startIssue: '1',
            endDate: '',
            endVolume: '',
            endIssue: '',
            coverageNote: ''
          ]
        ]
      ]

      TitleInstancePackagePlatform tipp1 = tippUpsertService.upsertDTO(tipp1_map)

      tipp1.title = journal
      tipp1.ids.addAll([issn, eissn])
      tipp1.save(flush: true)

      def tipp2_map = [
        pkg: testPkg.id,
        hostPlatform: testPlt.id,
        name: 'PackageService Journal 2',
        url: 'https://package-caching-test.test/journal2',
        editStatus: 'Approved',
        publicationType: 'Serial',
        importId: 'pcsJ2',
        hybridOA: 'No',
        paymentType: 'Unknown',
        accessStartDate: '2021-01-01',
        accessEndDate: '',
        subjectArea: 'Subject1',
        series: 'Series1',
        publisherName: 'PackageService Test Org',
        medium: 'Book',
        coverage: [
          [
            coverageDepth: 'fulltext',
            startDate: '2022-01',
            startVolume: '2',
            startIssue: '1',
            endDate: '',
            endVolume: '',
            endIssue: '',
            coverageNote: ''
          ]
        ]
      ]

      TitleInstancePackagePlatform tipp2 = tippUpsertService.upsertDTO(tipp2_map)

      tipp2.title = journal2
      tipp2.ids.addAll([eissn2])
      tipp2.save(flush: true)
    }
  }

  def cleanup() {
    [
      "PackageCachingService Journal 1",
      "PackageCachingService Journal 2",
      "PackageCachingService Update Journal",
      "PackageCachingService Book 1",
      "PackageCachingService Update Book",
    ].each {
      TitleInstancePackagePlatform.findByName(it)?.expunge()
    }
    Package.findByName("PackageCachingService Test Package")?.expunge()
    Platform.findByName("PackageCachingService Test Platform")?.expunge()
    Org.findByName("PackageCachingService Test Org")?.expunge()
    BookInstance.findByName("PackageCachingService Book 1")?.expunge()
    JournalInstance.findByName("PackageCachingService Journal 1")?.expunge()
    JournalInstance.findByName("PackageCachingService Journal 2")?.expunge()
  }
}