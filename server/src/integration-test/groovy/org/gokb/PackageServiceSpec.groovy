import com.k_int.ConcurrencyManagerService
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder

import gokbg3.DateFormatService

import grails.testing.mixin.integration.Integration
import grails.testing.services.ServiceUnitTest

import java.nio.file.Files
import java.nio.file.Paths
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
class PackageServiceSpec extends Specification {

  @Autowired
  PackageService packageService

  @Autowired
  TippService tippService

  @Autowired
  TippUpsertService tippUpsertService

  @Autowired
  DateFormatService dateFormatService

  @Autowired
  SessionFactory sessionFactory

  @Autowired
  ConcurrencyManagerService concurrencyManagerService

  def grailsApplication

  String filePath

  IdentifierNamespace issn_ns
  IdentifierNamespace eissn_ns
  IdentifierNamespace isbn_ns

  Identifier isbn
  Identifier issn
  Identifier eissn
  Identifier eissn2

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

    issn = Identifier.findByNamespaceAndValue(issn_ns, '0128-5483') ?: new Identifier(namespace: issn_ns, value: '0128-5483')
    eissn = Identifier.findByNamespaceAndValue(eissn_ns, '2180-4338') ?: new Identifier(namespace: eissn_ns, value: '2180-4338')
    eissn2 = Identifier.findByNamespaceAndValue(eissn_ns, '1727-9445') ?: new Identifier(namespace: eissn_ns, value: '1727-9445')

    if (!BookInstance.findByName('PackageService Book 1')) {
      isbn = new Identifier(namespace: isbn_ns, value: '979-11-655-6390-5').save(flush: true)
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
  }


  def cleanup() {
    [
      'PackageService Journal 1',
      'PackageService Journal 2',
      'PackageService Update Journal',
      'PackageService Book 1',
      'PackageService Update Book',
    ].each {
      TitleInstancePackagePlatform.findByName(it)?.expunge()
    }
    Package.findByName('PackageService Test Package')?.expunge()
    Platform.findByName('PackageService Test Platform')?.expunge()
    Org.findByName('PackageService Test Org')?.expunge()
    BookInstance.findByName('PackageService Book 1')?.expunge()
    BookInstance.findByName('PackageService Update Book')?.expunge()
    JournalInstance.findByName('PackageService Journal 1')?.expunge()
    JournalInstance.findByName('PackageService Journal 2')?.expunge()
  }

  void 'Test caching new TIPP KBART - check header'() {
    when:
    sleep(1000)
    packageService.createKbartExport(testPkg)

    then:
    sleep(3000)
    def latest_filename = packageService.getLatestFile(filePath, packageService.generateExportFileName(testPkg, PackageService.ExportType.KBART_TIPP))
    def file = new File(filePath + latest_filename)

    file.isFile()

    def csv = packageService.initReader(filePath + latest_filename)
    String[] header = csv.readNext().collect { it.toLowerCase().trim() }
    def col_positions = [:]
    int col_ctr = 0

    header.each { col ->
      col == packageService.KBART_FIELDS[col_ctr]
      col_positions[col] = col_ctr++
    }

    csv.close()
    file.delete()
  }

  void "Test caching new TIPP KBART - test lines"() {
    when:
    sleep(1000)
    packageService.createKbartExport(testPkg)

    then:
    sleep(3000)
    def latest_filename = packageService.getLatestFile(filePath, packageService.generateExportFileName(testPkg, PackageService.ExportType.KBART_TIPP))
    def file = new File(filePath + latest_filename)

    file.isFile()

    def csv = packageService.initReader(filePath + latest_filename)
    String[] header = csv.readNext().collect { it.toLowerCase().trim() }
    def col_positions = [:]
    int col_ctr = 0

    header.each { col ->
      col_positions[col] = col_ctr++
    }

    String[] row_data = csv.readNext()

    row_data[col_positions['publication_title']] == 'PackageService Book 1'
    row_data[col_positions['print_identifier']] == ''
    row_data[col_positions['online_identifier']] == '979-11-655-6390-5'
    row_data[col_positions['date_first_issue_online']] == ''
    row_data[col_positions['num_first_vol_online']] == ''
    row_data[col_positions['num_first_issue_online']] == ''
    row_data[col_positions['num_last_vol_online']] == ''
    row_data[col_positions['num_last_issue_online']] == ''
    row_data[col_positions['date_last_issue_online']] == ''
    row_data[col_positions['title_url']] == 'https://package-caching-test.test/book1'
    row_data[col_positions['first_author']] == 'Author1'
    row_data[col_positions['title_id']] == 'pcsB1'
    row_data[col_positions['embargo_info']] == ''
    row_data[col_positions['coverage_depth']] == 'fulltext'
    row_data[col_positions['coverage_notes']] == ''
    row_data[col_positions['publisher_name']] == 'PackageService Test Org'
    row_data[col_positions['publication_type']] == 'Monograph'
    row_data[col_positions['date_monograph_published_print']] == '2001-01-01'
    row_data[col_positions['date_monograph_published_online']] == '2019-01-01'
    row_data[col_positions['monograph_volume']] == '1'
    row_data[col_positions['first_editor']] == 'Editor1'
    row_data[col_positions['parent_publication_title_id']] == ''
    row_data[col_positions['preceding_publication_title_id']] == ''
    row_data[col_positions['access_type']] == 'P'

    csv.readNext() == null
    csv.close()
    file.delete()
  }

  void "Test caching updated TIPP KBART - new TIPPs"() {
    when:
    sleep(1000)
    packageService.createKbartExport(testPkg)
    sleep(1000)

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

    sleep(1000)
    packageService.createKbartExport(testPkg)

    then:
    sleep(3000)
    def latest_filename = packageService.getLatestFile(filePath, packageService.generateExportFileName(testPkg, PackageService.ExportType.KBART_TIPP))
    def file = new File(filePath + latest_filename)

    file.isFile()

    def csv = packageService.initReader(filePath + latest_filename)
    String[] header = csv.readNext().collect { it.toLowerCase().trim() }
    def col_positions = [:]
    int col_ctr = 0

    header.each { col ->
      col_positions[col] = col_ctr++
    }

    String[] row_data = csv.readNext()

    row_data[col_positions['publication_title']] == 'PackageService Book 1'
    row_data[col_positions['print_identifier']] == ''
    row_data[col_positions['online_identifier']] == '979-11-655-6390-5'
    row_data[col_positions['date_first_issue_online']] == ''
    row_data[col_positions['num_first_vol_online']] == ''
    row_data[col_positions['num_first_issue_online']] == ''
    row_data[col_positions['num_last_vol_online']] == ''
    row_data[col_positions['num_last_issue_online']] == ''
    row_data[col_positions['date_last_issue_online']] == ''
    row_data[col_positions['title_url']] == 'https://package-caching-test.test/book1'
    row_data[col_positions['first_author']] == 'Author1'
    row_data[col_positions['title_id']] == 'pcsB1'
    row_data[col_positions['embargo_info']] == ''
    row_data[col_positions['coverage_depth']] == 'fulltext'
    row_data[col_positions['coverage_notes']] == ''
    row_data[col_positions['publisher_name']] == 'PackageService Test Org'
    row_data[col_positions['publication_type']] == 'Monograph'
    row_data[col_positions['date_monograph_published_print']] == '2001-01-01'
    row_data[col_positions['date_monograph_published_online']] == '2019-01-01'
    row_data[col_positions['monograph_volume']] == '1'
    row_data[col_positions['first_editor']] == 'Editor1'
    row_data[col_positions['parent_publication_title_id']] == ''
    row_data[col_positions['preceding_publication_title_id']] == ''
    row_data[col_positions['access_type']] == 'P'

    row_data = csv.readNext()

    row_data != null
    row_data[col_positions['publication_title']] == 'PackageService Journal 1'
    row_data[col_positions['print_identifier']] == ''
    row_data[col_positions['online_identifier']] == '979-11-655-6390-5'
    row_data[col_positions['date_first_issue_online']] == ''
    row_data[col_positions['num_first_vol_online']] == ''
    row_data[col_positions['num_first_issue_online']] == ''
    row_data[col_positions['num_last_vol_online']] == ''
    row_data[col_positions['num_last_issue_online']] == ''
    row_data[col_positions['date_last_issue_online']] == ''
    row_data[col_positions['title_url']] == 'https://package-caching-test.test/journal1'
    row_data[col_positions['first_author']] == 'Author1'
    row_data[col_positions['title_id']] == 'pcsB1'
    row_data[col_positions['embargo_info']] == ''
    row_data[col_positions['coverage_depth']] == 'fulltext'
    row_data[col_positions['coverage_notes']] == ''
    row_data[col_positions['publisher_name']] == 'PackageService Test Org'
    row_data[col_positions['publication_type']] == 'Monograph'
    row_data[col_positions['date_monograph_published_print']] == '2001-01-01'
    row_data[col_positions['date_monograph_published_online']] == '2019-01-01'
    row_data[col_positions['monograph_volume']] == '1'
    row_data[col_positions['first_editor']] == 'Editor1'
    row_data[col_positions['parent_publication_title_id']] == ''
    row_data[col_positions['preceding_publication_title_id']] == ''
    row_data[col_positions['access_type']] == 'P'

    csv.close()
    file.delete()
  }
}