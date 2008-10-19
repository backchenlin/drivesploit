##
# This file is part of the Metasploit Framework and may be subject to 
# redistribution and commercial restrictions. Please see the Metasploit
# Framework web site for more information on licensing and terms of use.
# http://metasploit.com/projects/Framework/
##


require 'msf/core'


class Metasploit3 < Msf::Auxiliary

	include Msf::Exploit::Remote::Ftp
	include Msf::Auxiliary::Scanner

	def initialize
		super(
			'Name'        => 'Anonymous FTP Access Detection',
			'Version'     => '$Revision$',
			'Description' => 'Detect anonymous (read/write) FTP server access.',
			'References'  =>
				[
					['URL', 'http://en.wikipedia.org/wiki/File_Transfer_Protocol#Anonymous_FTP'],
				],
			'Author'      => 'Matteo Cantoni <goony[at]nothink.org>',
			'License'     => MSF_LICENSE
		)
	
		register_options(
			[
				Opt::RPORT(21),
			], self.class)
	end

	def run_host(target_host)

		begin
		
		res = connect_login(true, false)

		banner.strip! if banner

		dir = Rex::Text.rand_text_alpha(8)
		if res 
			write_check = send_cmd( ['MKD', dir] , true)

			if (write_check and write_check =~ /^2/)
				send_cmd( ['RMD', dir] , true)
				p write_check
				print_status("#{target_host}:#{rport} Anonymous READ/WRITE (#{banner})")
			else
				print_status("#{target_host}:#{rport} Anonymous READ (#{banner})")
			end
		end

		disconnect
		
		rescue ::Interrupt
			raise $!
		rescue ::Exception => e
		end
		
	end
end