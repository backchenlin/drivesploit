module Msf
###
#
# This module provides methods for WMAP-enabled modules
#
###

module Auxiliary::WMAPModule
	#
	# Initializes an instance of a WMAP module
	#
	def initialize(info = {})
		super
	end

	def wmap_enabled
		#enabled by default
		true
	end

	def wmap_type
		#default type
		nil
	end
	
	#modified from CGI.rb as we dont use arrays, this function may need to be included in proto/http
	def headersparse(qheaders)
		params = Hash.new()

 		qheaders.split(/[&;]/n).each do |pairs|
 			key, value = pairs.split(':',2)
 			if params.has_key?(key)
				#Error
 			else
				params[key] = value
 			end
 		end
		params
	end

	#modified from CGI.rb as we dont use arrays, this function may need to be included in proto/http
	def queryparse(query)
		params = Hash.new()

 		query.split(/[&;]/n).each do |pairs|
 			key, value = pairs.split('=',2)
 			if params.has_key?(key)
				#Error
 			else
				params[key] = value
 			end
 		end
		params
	end

   	# Levenshtein distance algorithm  (slow, huge mem consuption)
   	def distance(a, b)
   		case
   		when a.empty?: b.length
   		when b.empty?: a.length
   		else [(a[0] == b[0] ? 0 : 1) + distance(a[1..-1], b[1..-1]),
			1 + distance(a[1..-1], b),
			2 + distance(a, b[1..-1])].min
   		end
   	end
				
end

###
#
# This module provides methods for WMAP File Scanner modules
#
###

module Auxiliary::WMAPScanFile
	include Auxiliary::WMAPModule

	def wmap_type
		:WMAP_FILE
	end 
end

###
#
# This module provides methods for WMAP Directory Scanner modules
#
###

module Auxiliary::WMAPScanDir
	include Auxiliary::WMAPModule

	def wmap_type
		:WMAP_DIR
	end 
end

###
#
# This module provides methods for WMAP Web Server Scanner modules
#
###

module Auxiliary::WMAPScanServer
	include Auxiliary::WMAPModule

	def wmap_type
		:WMAP_SERVER
	end 
end

###
#
# This module provides methods for WMAP Query Scanner modules
#
###

module Auxiliary::WMAPScanQuery
	include Auxiliary::WMAPModule

	def wmap_type
		:WMAP_QUERY
	end 
end

###
#
# This module provides methods for WMAP Body Scanner modules
#
###

module Auxiliary::WMAPScanBody
	include Auxiliary::WMAPModule

	def wmap_type
		:WMAP_BODY
	end 
end

###
#
# This module provides methods for WMAP Headers Scanner modules
#
###

module Auxiliary::WMAPScanHeaders
	include Auxiliary::WMAPModule

	def wmap_type
		:WMAP_HEADERS
	end 
end

end
