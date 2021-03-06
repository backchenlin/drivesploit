##
# $Id$
##

##
# This file is part of the Metasploit Framework and may be subject to
# redistribution and commercial restrictions. Please see the Metasploit
# Framework web site for more information on licensing and terms of use.
# http://metasploit.com/framework/
##

require 'msf/core'
require 'racket'

class Metasploit3 < Msf::Auxiliary

	include Msf::Exploit::Capture
	include Msf::Auxiliary::Scanner
	include Msf::Auxiliary::Report

	def initialize
		super(
			'Name'        => 'TCP ACK Firewall Scanner',
			'Description' => %q{
				Map out firewall rulesets with a raw ACK scan.  Any
				unfiltered ports found means a stateful firewall is
				not in place for them.
			},
			'Author'      => 'kris katterjohn',
			'Version'     => '$Revision$', # 03/26/2009
			'License'     => MSF_LICENSE
		)

		begin
			require 'pcaprub'
			@@havepcap = true
		rescue ::LoadError
			@@havepcap = false
		end

		register_options([
			OptString.new('PORTS', [true, "Ports to scan (e.g. 22-25,80,110-900)", "1-10000"]),
			OptInt.new('TIMEOUT', [true, "The reply read timeout in milliseconds", 500]),
			OptInt.new('BATCHSIZE', [true, "The number of hosts to scan per set", 256]),
			OptString.new('INTERFACE', [false, 'The name of the interface'])
		], self.class)

		deregister_options('FILTER','PCAPFILE')
	end

	def run_batch_size
		datastore['BATCHSIZE'] || 256
	end

	def run_batch(hosts)
		open_pcap

		raise "Pcaprub is not available" if not @@havepcap

		pcap = self.capture

		ports = Rex::Socket.portspec_crack(datastore['PORTS'])

		if ports.empty?
			print_error("Error: No valid ports specified")
			return
		end

		to = (datastore['TIMEOUT'] || 500).to_f / 1000.0

		# Spread the load across the hosts
		ports.each do |dport|
			hosts.each do |dhost|
				shost, sport = getsource(dhost)

				pcap.setfilter(getfilter(shost, sport, dhost, dport))

				begin
					probe = buildprobe(shost, sport, dhost, dport)

					capture_sendto(probe, dhost)

					reply = probereply(pcap, to)

					next if not reply

					print_status(" TCP UNFILTERED #{dhost}:#{dport}")

					#Add Report
					report_note(
						:host	=> dhost,
						:proto	=> 'tcp',
						:port	=> dport,
						:type	=> "TCP UNFILTERED #{dhost}:#{dport}",
						:data	=> "TCP UNFILTERED #{dhost}:#{dport}"
					)

				rescue ::Exception
					print_error("Error: #{$!.class} #{$!}")
				end
			end
		end

		close_pcap
	end

	def getfilter(shost, sport, dhost, dport)
		# Look for associated RSTs
		"tcp and (tcp[13] & 0x04) != 0 and " +
		"src host #{dhost} and src port #{dport} and " +
		"dst host #{shost} and dst port #{sport}"
	end

	def getsource(dhost)
		# srcip, srcport
		[ Rex::Socket.source_address(dhost), rand(0xffff - 1025) + 1025 ]
	end

	def buildprobe(shost, sport, dhost, dport)
		n = Racket::Racket.new

		n.l3 = Racket::L3::IPv4.new
		n.l3.src_ip = shost
		n.l3.dst_ip = dhost
		n.l3.protocol = 0x6
		n.l3.id = rand(0x10000)
		n.l3.ttl = 255

		n.l4 = Racket::L4::TCP.new
		n.l4.src_port = sport
		n.l4.seq = rand(0x100000000)
		n.l4.ack = rand(0x100000000)
		n.l4.flag_ack = 1
		n.l4.dst_port = dport
		n.l4.window = 3072

		n.l4.fix!(n.l3.src_ip, n.l3.dst_ip, "")

		n.pack
	end

	def probereply(pcap, to)
		reply = nil

		begin
			Timeout.timeout(to) do
				pcap.each do |r|
					eth = Racket::L2::Ethernet.new(r)
					next if not eth.ethertype == 0x0800

					ip = Racket::L3::IPv4.new(eth.payload)
					next if not ip.protocol == 6

					tcp = Racket::L4::TCP.new(ip.payload)

					reply = {:raw => r, :eth => eth, :ip => ip, :tcp => tcp}

					break
				end
			end
		rescue Timeout::Error
		end

		return reply
	end
end

