-- Copyright (C) 2007-2013 by Simon Hefti. All rights reserved.
--
-- Licensed under the EPL 1.0 (Eclipse Public License).
-- (see http://www.eclipse.org/legal/epl-v10.html)
--
-- Software distributed under the License is distributed on an "AS IS"
-- basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
--
-- Initial Developer: Simon Hefti

-- thumbnail cache
create table thumbnail (
  path text,
  image blob,
  height integer,
  mimetype text
);

-- list of known fotos
create virtual table foto using fts4 (
  path text not null, -- path on file system, e.g. /foo/bar/1.jpg
  noteid text, -- evernote note ID,e.g. aaaaaaa-aaaa-aaaa-aaaa-aaaaaaa
  mimetype text, -- mime type of this file, e.g. image/jpeg
  creationdate text, -- format yyyy-MM-dd'T'HHmm
  make text, -- camera make, e.g. Nikon
  model text, -- camera model, e.g. Sinarback 54 M, Hasselblad
  geo_long text, -- longitude in degrees, e.g. 10.0 (west is negative)
  geo_lat text, -- latitude in degrees, e.g. 40.0 (south is negative)
  orientation text,  -- orientation hint, e.g. right side (Rotate 180)
  category text, -- likes, e.g. best-of
  note text
);

-- used to keep track of current schema version
create table conf (
  k text, -- key
  v text  -- value
);

insert into conf (k,v) values ('version',1);