@Grapes([
 @Grab(group='org.xerial',module='sqlite-jdbc',version='3.7.2'),
 @GrabConfig(systemClassLoader=true)
])
 
/**
 *
 * Textra => SMS Backup and Restore => Android Stock - conversion script
 *
 * 2015 Ben Jones / octopod.org
 * http://github.com/textra-to-sms-backup-and-restore-conversion-script
 *
 * A very rough script. Inefficient and cobbled together. But did the job of a one-off
 * conversion from a Textra database, to SMS backup and restore XML format, which allowed
 * me to import the messages back into the android messages database.
 *
 * Perhaps some issues remain - out of 13k messages I had about 10 fail to import and a couple
 * of conversations show 'loading mms' every time I open them. I don't know if it was the conversion
 * or if they were bad in the original. But those results were good enough for me.
 *
 * Instructions:
 * Change the variables 'pathToTextraData' and 'myMobileNumber'
 * Run the export to read your textra data and produce an XML file
 * Import that XML file onto your device using SMS Backup and restore
 *
 * Thanks to
 * Textra - http://www.textra.me/
 * SMS Backup and Restore - http://android.riteshsahu.com/apps/sms-backup-restore
 *
 * Both great apps. It was my ignorance with backups which left me with only a Textra database
 * and not a stock messaging database.
 *
 * Tested on Textra version 2.5, SMS Backup and Restore version 7.42, and messaging database 
 * from a Samsung S6 5.0 stock firmware.
 *
 */
 
import java.sql.*
import org.sqlite.SQLite
import groovy.sql.Sql

import groovy.xml.*
 
def pathToTextraData = "/Users/foo/Downloads/data"
def myMobileNumber = "+61432123456"
 
def xml = new StreamingMarkupBuilder()

def smsRows = []
 
def sql = Sql.newInstance(
	"jdbc:sqlite:" + pathToTextraData + "/data/com.textra/databases/messaging.db", 
	"org.sqlite.JDBC"
)

def smsList = []

sql.eachRow("select m.*, c.lookup_key, c.display_name from messages as m join convos as c on m.convo_id=c._id") {  r->
  if (r.part_content_type) {
    def file = new File( pathToTextraData + "/data/com.textra/files/db/media-body/${r.convo_id}/${r._id}")
    def incoming = !r.direction==1
    smsRows += """
<mms 
  reserved="0" 
  text_only="1" 
  msg_id="0" 
  ct_t="application/vnd.wap.multipart.related" 
  msg_box="2" 
  secret_mode="0" 
  hidden="0" 
  v="${incoming?'16':'18'}" 
  sub="" 
  seen="1" 
  rr="${incoming?'129':'null'}" 
  ct_cls="null" 
  retr_txt_cs="null" 
  ct_l="null" 
  m_size="${file.length()}" 
  exp="${incoming?'null':'604800'}" 
  deletable="0" 
  sub_cs="null" 
  sim_imsi="null" 
  st="null" 
  creator="null" 
  tr_id="T${Long.toHexString(r.ts)}" 
  sub_id="-1" 
  sim_slot="0" 
  read="1" 
  app_id="0" 
  resp_st="${incoming?'null':'128'}" 
  date="${r.ts}" 
  m_id="${r.mms_unique_id}" 
  date_sent="0" 
  callback_set="0" 
  pri="129" 
  m_type="${incoming?'132':'128'}" 
  address="${r.lookup_key.replace('^','')}" 
  d_rpt="129" 
  d_tm="null" 
  read_status="null" 
  spam_report="0" 
  locked="0" 
  retr_txt="null" 
  resp_txt="null" 
  safe_message="0" 
  rpt_a="null" 
  retr_st="null" 
  m_cls="personal" 
  readable_date="${new Date(r.ts).toGMTString()}" 
  contact_name="${r.display_name}">
    <parts>
      <part 
        seq="0" 
        ct="${r.part_content_type}" 
        name="null" 
        chset="null" 
        cd="null" 
        fn="${r.part_filename}" 
        cid="&lt;${r.part_filename}&gt;" 
        cl="${r.part_filename}" 
        ctt_s="null" 
        ctt_t="null" 
        text="null" 
        data="${file.text.bytes.encodeBase64().toString()}" 
      />
    </parts>
    <addrs>
      <addr 
        address="${incoming?r.lookup_key.replace('^',''):'insert-address-token'}" 
        type="137" 
        charset="106" 
      />
      <addr 
        address="${myMobileNumber}" 
        type="151" 
        charset="106" 
      />
    </addrs>
  </mms>
    """
  } else {
    def sms = { sms(
      protocol: 0,
      address: r.lookup_key.replace('^',''),
      date: r.ts,
      type: r.direction==1 ? 2 : 1,
      subject: 'null',
      body: r.text,
      toa: 'null',
      sc_toa: 'null',
      service_center: r.message_center_ts,
      read: '1',
      status: '0',
      readable_date: new Date(r.ts).toGMTString(),
      contact_name: r.display_name
    )}
      
    smsRows += "\n" + xml.bind(sms).toString()
  }
}
 
println "writing ${smsRows.size()} rows"

def o = new File(pathToTextraData + "/all-messages.xml")

o << """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<?xml-stylesheet type="text/xsl" href="sms.xsl"?>"""
o << "<smses count='"+smsRows.size()+"'>"
smsRows.each{ o << it }
o << "</smses>"
