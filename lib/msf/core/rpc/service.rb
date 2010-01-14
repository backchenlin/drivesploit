require "xmlrpc/server"
require 'rex/service_manager'
require "rex"


module Msf
module RPC
class Service < ::XMLRPC::BasicServer

	attr_accessor :service, :state

	def initialize(srvhost, srvport, ssl=false, cert=nil, ckey=nil)
		self.service = Rex::Socket::TcpServer.create(
			'LocalHost' => srvhost,
			'LocalPort' => srvport,
			'SSL'       => ssl
		)

		self.service.on_client_connect_proc = Proc.new { |client|
			on_client_connect(client)
		}
		self.service.on_client_data_proc = Proc.new { |client|
			on_client_data(client)
		}
		self.service.on_client_close_proc = Proc.new { |client|
			on_client_close(client)
		}

		self.state = {}
		super()
	end

	def start
		self.state = {}
		self.service.start
	end

	def stop
		self.state = {}
		self.service.stop
	end

	def wait
		self.service.wait
	end

	def on_client_close(c)
		self.state.delete(c)
	end

	def on_client_connect(c)
		self.state[c] = ""
	end

	def on_client_data(c)
		data = c.get_once(-1)
		if(not data)
			self.c.close
			return
		end
		self.state[c] << data
		procxml(c)
	end

	def procxml(c)
		while(self.state[c].index("\x00"))
			mesg,left = self.state[c].split("\x00", 2)
			self.state[c] = left
			begin
				res = process(mesg)
			rescue ::Exception => e
				$stderr.puts "ERROR: #{e.class} #{e}"
			end
			c.put(res+"\x00")
		end
	end

end

class WebService < ::XMLRPC::BasicServer

	attr_accessor :service, :state, :srvhost, :srvport, :uri


	def initialize(port, host, uri = "/RPC2")
		self.srvhost = host
		self.srvport = port
		self.uri = uri
		self.service = nil
		super()
	end

	def start
		self.state = {}
		self.service = Rex::ServiceManager.start(
			Rex::Proto::Http::Server,
			self.srvport,
			self.srvhost,
			{}
		)

		uopts = {
			'Proc' => Proc.new { |cli, req|
				on_request_uri(cli, req)
			},
			'Path' => self.uri
		}

		self.service.add_resource(self.uri,uopts)
	end

	def stop
		self.state = {}
		self.service.stop
	end

	def wait
		self.service.wait
	end

	def on_client_close(c)
		self.state.delete(c)
	end

	def on_client_connect(c)
		self.state[c] = ""
	end
	def on_request_uri(cli, req)
		begin
			res = Rex::Proto::Http::Response.new()
			res["Content-Type"] = "text/xml"
			res.body = process(req.body)
		rescue XMLRPC::FaultException => e
			res = Rex::Proto::Http::Response.new(e.faultCode,e.faultString)
		rescue
			res = Rex::Proto::Http::Response.new(404,"An Error Occured")
		end
		cli.send_response(res)
	end

end

end
end

