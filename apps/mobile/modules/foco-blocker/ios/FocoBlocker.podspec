Pod::Spec.new do |s|
  s.name           = 'FocoBlocker'
  s.version        = '0.1.0'
  s.summary        = 'Foco app blocking (Screen Time) for iOS'
  s.description    = 'FamilyControls + ManagedSettings bridge used by Foco'
  s.license        = 'UNLICENSED'
  s.author         = 'Foco'
  s.homepage       = 'https://github.com/cazadoxs/programar'
  s.platforms      = { :ios => '16.4' }
  s.swift_version  = '5.9'
  s.source         = { git: '' }
  s.static_framework = true
  s.dependency 'ExpoModulesCore'
  s.frameworks     = 'FamilyControls', 'ManagedSettings', 'SwiftUI'
  s.source_files   = '**/*.swift'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'SWIFT_COMPILATION_MODE' => 'wholemodule' }
end
