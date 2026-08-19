import docx
from docx.shared import Pt
from docx.enum.text import WD_ALIGN_PARAGRAPH

def create_cv():
    doc = docx.Document()
    
    # Configure default style
    style = doc.styles['Normal']
    font = style.font
    font.name = 'Calibri'
    font.size = Pt(11)

    # Read the CV markdown
    with open('CV.md', 'r', encoding='utf-8') as f:
        lines = f.readlines()

    for line in lines:
        line = line.strip()
        
        # Add a paragraph for each line to maintain formatting
        # Skip horizontal lines
        if line.startswith('---'):
            continue
            
        p = doc.add_paragraph()
        
        # Check for headings
        if line == 'PROFESSIONAL SUMMARY' or \
           line == 'TECHNICAL SKILLS' or \
           line == 'PROJECT EXPERIENCE' or \
           line == 'EDUCATION & CERTIFICATIONS' or \
           line == 'HONORS & AWARDS':
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            run = p.add_run(line)
            run.bold = True
            run.font.size = Pt(12)
            # Add a bottom border/line (we'll just use underline for simplicity)
            # Or just add a line space
            doc.add_paragraph()
            
        # Check for name (first line)
        elif line == 'PHAN LE THANH NGUYEN':
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run = p.add_run(line)
            run.bold = True
            run.font.size = Pt(16)
        
        # Check for role (second line)
        elif line == 'Embedded Software Engineer':
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run = p.add_run(line)
            run.bold = True
            run.font.size = Pt(12)
            
        # Check for contact info (third and fourth lines)
        elif '|' in line and ('Da Nang' in line or 'GitHub' in line):
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run = p.add_run(line)
            
        # Bullet points
        elif line.startswith('•'):
            p.style = 'List Bullet'
            # Check for bold prefixes like "Languages & OS:"
            if ':' in line and len(line.split(':')[0]) < 30:
                parts = line.split(':', 1)
                run1 = p.add_run(parts[0] + ':')
                run1.bold = True
                p.add_run(parts[1])
            else:
                p.add_run(line[1:].strip())
                
        # Project titles
        elif '|' in line and ('Jan' in line or 'May' in line):
            parts = line.split('|')
            run1 = p.add_run(parts[0] + '|')
            run1.bold = True
            run2 = p.add_run(parts[1])
            
        # Project roles
        elif '|' in line and ('Tech:' in line):
            parts = line.split('|')
            run1 = p.add_run(parts[0] + '|')
            run1.italic = True
            run2 = p.add_run(parts[1])
            
        # Education
        elif 'Bachelor of Computing' in line:
            run = p.add_run(line)
            run.bold = True
            
        # Default text
        else:
            p.add_run(line)
            
    doc.save('PHAN_LE_THANH_NGUYEN_CV.docx')

if __name__ == '__main__':
    create_cv()
